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
 * <p>Algorithm — intentionally simple, no ML:
 *   <ul>
 *     <li>Look at the last N=30 days of SALE stock movements per product.</li>
 *     <li>Average daily velocity = soldQty / 30.</li>
 *     <li>Days-of-stock = currentQty / velocity (∞ if velocity = 0).</li>
 *     <li>If days-of-stock ≤ {@code lead_time} (default 7), the product
 *         is a re-order candidate — suggested quantity is
 *         {@code ceil(velocity * (lead_time + reorder_window))}.</li>
 *     <li>Slow movers: products with velocity &lt; 0.05 and stock &gt; 0
 *         that have been sitting on the shelf &gt; 30 days.</li>
 *   </ul>
 *
 * <p>Why no ML: 1) we don't have enough history for most SKUs in a
 * small shop, 2) seasonal effects dominate and would require a
 * domain model we don't have, 3) simple velocity is interpretable.
 */
@Service
@Transactional(readOnly = true)
public class ForecastService {

    private static final int WINDOW_DAYS = 30;
    private static final int LEAD_TIME_DAYS = 7;
    private static final int REORDER_WINDOW_DAYS = 14;

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
            double dailyVelocity,
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
        Map<Long, Double> sold = soldByProduct();
        List<ProductForecast> out = new ArrayList<>();
        for (Product p : products.findAll()) {
            double soldN = sold.getOrDefault(p.getId(), 0.0);
            double velocity = soldN / WINDOW_DAYS;
            Integer days = velocity > 0
                    ? (int) Math.floor(p.getQuantity() / velocity)
                    : null;
            LocalDate runOut = (days != null && days < 365)
                    ? LocalDate.now().plusDays(days)
                    : null;
            boolean reorder = days != null && days <= LEAD_TIME_DAYS;
            int suggestedQty = reorder
                    ? (int) Math.ceil(velocity * (LEAD_TIME_DAYS + REORDER_WINDOW_DAYS))
                    : 0;
            out.add(new ProductForecast(
                    p.getId(), p.getName(), p.getQuantity(),
                    soldN, velocity, days, runOut, reorder, suggestedQty));
        }
        // Surface re-order candidates first, then by days-of-stock asc.
        out.sort(Comparator
                .comparing(ProductForecast::reorderNeeded).reversed()
                .thenComparing(f -> f.daysOfStock() == null ? Integer.MAX_VALUE : f.daysOfStock()));
        return out;
    }

    public List<SlowMover> slowMovers() {
        Map<Long, Double> sold = soldByProduct();
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

    private Map<Long, Double> soldByProduct() {
        LocalDateTime from = LocalDate.now().minusDays(WINDOW_DAYS).atStartOfDay();
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
}
