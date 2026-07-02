package uz.barakat.market.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.domain.Product;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.StockMovementRepository;

/**
 * Demand forecasting + auto-PO suggestions + slow-mover detection.
 *
 * <p>Algorithm — a recency-weighted moving average; deliberately NOT ML
 * (market this as "avtomatik prognoz", not "machine learning"):
 *   <ul>
 *     <li>Two windows of SALE stock movements per product: the recent
 *         {@code RECENT_DAYS}=14 and the full {@code WINDOW_DAYS}=30.</li>
 *     <li>Blended velocity = {@code 0.6·recent14/14 + 0.4·full30/30} — a
 *         product that just started (or stopped) selling shifts the forecast
 *         within days instead of being diluted across a flat month.</li>
 *     <li>Trend factor = recent daily rate ÷ prior-16-days daily rate
 *         (1.0 = steady, &gt;1 rising, &lt;1 fading) — surfaced so the UI/AI
 *         can say "sotuvi o'sib boryapti".</li>
 *     <li>Days-of-stock = currentQty / blended velocity (∞ if 0).</li>
 *     <li>Re-order when days-of-stock ≤ {@code lead_time} (7): suggested
 *         qty = {@code ceil(velocity · (lead_time + reorder_window))},
 *         nudged up 25% when the trend is clearly rising (≥1.5×) so a
 *         taking-off product doesn't get under-ordered.</li>
 *     <li>Slow movers: velocity &lt; 0.05 with stock &gt; 0.</li>
 *   </ul>
 *
 * <p>Why still no ML: 1) most SKUs in a small shop lack the history,
 * 2) a weighted average is inspectable by the owner, 3) the error that
 * matters (running out of a rising product) is exactly what the recency
 * weight + trend nudge fix.
 */
@Service
@Transactional(readOnly = true)
public class ForecastService {

    private static final int WINDOW_DAYS = 30;
    private static final int RECENT_DAYS = 14;
    private static final double RECENT_WEIGHT = 0.6;
    private static final int LEAD_TIME_DAYS = 7;
    private static final int REORDER_WINDOW_DAYS = 14;
    private static final double RISING_TREND = 1.5;

    private final ProductRepository products;
    private final StockMovementRepository movements;

    public ForecastService(ProductRepository products, StockMovementRepository movements) {
        this.products = products;
        this.movements = movements;
    }

    public record ProductForecast(
            Long productId,
            String name,
            int currentQty,
            double soldLast30Days,
            double dailyVelocity,        // recency-weighted blend (see class doc)
            double trendFactor,          // 1.0 steady, >1 rising, <1 fading
            Integer daysOfStock,         // null = infinite (no sales)
            LocalDate predictedRunOut,    // null = never
            boolean reorderNeeded,
            int suggestedReorderQty) { }

    public record SlowMover(
            Long productId,
            String name,
            int currentQty,
            double soldLast30Days,
            double dailyVelocity,
            int suggestedDiscountPercent,
            String reason) { }

    public List<ProductForecast> forecast() {
        Map<Long, Double> soldFull = soldByProduct(WINDOW_DAYS);
        Map<Long, Double> soldRecent = soldByProduct(RECENT_DAYS);
        List<ProductForecast> out = new ArrayList<>();
        for (Product p : products.findAll()) {
            double soldN = soldFull.getOrDefault(p.getId(), 0.0);
            double recentN = soldRecent.getOrDefault(p.getId(), 0.0);
            double recentRate = recentN / RECENT_DAYS;
            double fullRate = soldN / WINDOW_DAYS;
            double velocity = RECENT_WEIGHT * recentRate + (1 - RECENT_WEIGHT) * fullRate;
            // Trend: recent daily rate vs the PRIOR (non-overlapping) days' rate.
            double olderN = Math.max(0, soldN - recentN);
            double olderRate = olderN / (WINDOW_DAYS - RECENT_DAYS);
            double trend = olderRate > 0 ? recentRate / olderRate
                    : (recentRate > 0 ? 2.0 : 1.0); // new mover with no history = rising
            Integer days = velocity > 0
                    ? (int) Math.floor(p.getQuantity() / velocity)
                    : null;
            LocalDate runOut = (days != null && days < 365)
                    ? LocalDate.now().plusDays(days)
                    : null;
            boolean reorder = days != null && days <= LEAD_TIME_DAYS;
            int suggestedQty = 0;
            if (reorder) {
                double base = velocity * (LEAD_TIME_DAYS + REORDER_WINDOW_DAYS);
                // A clearly-rising product gets a 25% cushion — the cost of
                // over-ordering a little is far below stocking out on a riser.
                suggestedQty = (int) Math.ceil(trend >= RISING_TREND ? base * 1.25 : base);
            }
            out.add(new ProductForecast(
                    p.getId(), p.getName(), p.getQuantity(),
                    soldN, velocity, round2(trend), days, runOut, reorder, suggestedQty));
        }
        // Surface re-order candidates first, then by days-of-stock asc.
        out.sort(Comparator
                .comparing(ProductForecast::reorderNeeded).reversed()
                .thenComparing(f -> f.daysOfStock() == null ? Integer.MAX_VALUE : f.daysOfStock()));
        return out;
    }

    public List<SlowMover> slowMovers() {
        Map<Long, Double> sold = soldByProduct(WINDOW_DAYS);
        List<SlowMover> out = new ArrayList<>();
        for (Product p : products.findAll()) {
            if (p.getQuantity() <= 0) continue;
            double soldN = sold.getOrDefault(p.getId(), 0.0);
            double velocity = soldN / WINDOW_DAYS;
            if (velocity >= 0.05) continue; // moves > 1.5 units per month, not slow
            // Days on shelf >= 30 (avg via velocity guard above is enough).
            int suggestedPct = soldN == 0 ? 25 : soldN < 2 ? 20 : 15;
            String reason = soldN == 0
                    ? "30 kun ichida hech sotilmagan"
                    : String.format("Faqat %.1f dona 30 kunda — juda sekin", soldN);
            out.add(new SlowMover(
                    p.getId(), p.getName(), p.getQuantity(),
                    soldN, velocity, suggestedPct, reason));
        }
        // Highest qty * lowest velocity wins (= the most "stuck capital").
        out.sort(Comparator.comparingDouble(s -> -(s.currentQty() / Math.max(0.01, s.dailyVelocity()))));
        return out;
    }

    /** Reorder candidates only — the subset of forecast() that needs action. */
    public List<ProductForecast> reorderQueue() {
        return forecast().stream().filter(ProductForecast::reorderNeeded).toList();
    }

    // ---------------------------------------------------------------- helpers

    private Map<Long, Double> soldByProduct(int windowDays) {
        LocalDateTime from = LocalDate.now().minusDays(windowDays).atStartOfDay();
        LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();
        Map<Long, Double> map = new HashMap<>();
        List<Long> scope = TenantContext.activeScope();
        if (scope.isEmpty()) return map;
        for (Object[] row : movements.sumSalesQtyByProduct(scope, from, to)) {
            Long pid = ((Number) row[0]).longValue();
            double qty = ((Number) row[1]).doubleValue();
            map.put(pid, qty);
        }
        return map;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
