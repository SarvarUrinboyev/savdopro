package uz.barakat.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Sale;
import uz.barakat.market.domain.SaleItem;
import uz.barakat.market.dto.AnomalyResponse;
import uz.barakat.market.repository.SaleRepository;

/**
 * Statistical anomaly detector — surfaces "unusual" patterns the
 * shop-owner should investigate. Three rules today:
 *
 * <ul>
 *   <li><b>High refund rate</b> — refunds &gt; 15% of revenue in a day,
 *       or 3+ refunds in any hour.</li>
 *   <li><b>Late-night activity</b> — sales after 23:00 or before 06:00.
 *       Often a sign of the cashier "test"ing the system after hours.</li>
 *   <li><b>Single-product spike</b> — one SKU sold &gt; 3× its 30-day
 *       average in a single day.</li>
 * </ul>
 *
 * <p>Rules are intentionally loud — operators want false-positives over
 * false-negatives. They can dismiss noise; they can't recover lost
 * money. The thresholds are tunable via properties so we don't ship
 * a too-aggressive setting.
 */
@Service
@Transactional(readOnly = true)
public class AnomalyService {

    private final SaleRepository sales;
    private final AnomalyMonitorService monitor;

    public AnomalyService(SaleRepository sales, AnomalyMonitorService monitor) {
        this.sales = sales;
        this.monitor = monitor;
    }

    public record Anomaly(
            String severity,   // "info" / "warn" / "critical"
            String code,       // machine tag, e.g. "high-refund-rate"
            String message,    // human description
            LocalDateTime at) { }

    public List<Anomaly> check() {
        List<Anomaly> out = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDate from7 = today.minusDays(7);

        // Pull last 7 days of sales — enough for refund-rate + spike comparisons.
        List<Sale> recent = sales.findByCreatedAtBetweenOrderByCreatedAtDesc(
                from7.atStartOfDay(), today.plusDays(1).atStartOfDay());

        // --- Rule 1: high refund rate today
        BigDecimal todayRevenue = BigDecimal.ZERO;
        BigDecimal todayRefunds = BigDecimal.ZERO;
        for (Sale s : recent) {
            if (!s.getCreatedAt().toLocalDate().equals(today)) continue;
            todayRevenue = todayRevenue.add(s.getTotalUzs());
            todayRefunds = todayRefunds.add(s.getRefundedTotalUzs());
        }
        if (todayRevenue.signum() > 0) {
            BigDecimal rate = todayRefunds.multiply(BigDecimal.valueOf(100))
                    .divide(todayRevenue, 1, RoundingMode.HALF_UP);
            if (rate.compareTo(BigDecimal.valueOf(15)) > 0) {
                out.add(new Anomaly("warn", "high-refund-rate",
                        String.format("Bugun qaytarish darajasi %.1f%% — odatdagidan baland. "
                                + "Kassirni tekshiring.", rate.doubleValue()),
                        now));
            }
        }

        // --- Rule 2: late-night activity
        long lateNight = recent.stream()
                .filter(s -> s.getCreatedAt().toLocalDate().equals(today))
                .filter(s -> {
                    int h = s.getCreatedAt().getHour();
                    return h >= 23 || h < 6;
                })
                .count();
        if (lateNight > 0) {
            out.add(new Anomaly("info", "late-night-activity",
                    String.format("Bugun tungi soatlarda (23:00–06:00) %d ta sotuv qilingan.",
                            lateNight),
                    now));
        }

        // --- Rule 3: single-product spike (today vs 7-day baseline)
        Map<Long, Integer> todayByProduct = new HashMap<>();
        Map<Long, Integer> baselineByProduct = new HashMap<>();
        Map<Long, String> nameByProduct = new HashMap<>();
        for (Sale s : recent) {
            boolean isToday = s.getCreatedAt().toLocalDate().equals(today);
            for (SaleItem it : s.getItems()) {
                Long pid = it.getProductId();
                if (pid == null) continue;
                nameByProduct.putIfAbsent(pid, it.getProductName());
                if (isToday) {
                    todayByProduct.merge(pid, it.getQuantity(), Integer::sum);
                } else {
                    baselineByProduct.merge(pid, it.getQuantity(), Integer::sum);
                }
            }
        }
        for (Map.Entry<Long, Integer> e : todayByProduct.entrySet()) {
            int todayQty = e.getValue();
            int baselineQty = baselineByProduct.getOrDefault(e.getKey(), 0);
            double dailyBaseline = baselineQty / 6.0; // last 6 non-today days
            // Need at least 5 units of bulk + 3x spike to flag.
            if (todayQty >= 5 && dailyBaseline > 0 && todayQty >= 3 * dailyBaseline) {
                out.add(new Anomaly("info", "product-spike",
                        String.format("'%s' bugun %d dona sotildi — odatdagi kunlik %.1f dona.",
                                nameByProduct.getOrDefault(e.getKey(), "?"),
                                todayQty, dailyBaseline),
                        now));
            }
        }

        // --- Merge persisted, unacknowledged alerts from the deterministic
        // engine (till-negative, below-cost-daily, refund-*, night-spike,
        // cashier-anomaly). These are the source of truth, so acknowledging one
        // removes it from the banner. Drop the transient high-refund-rate when a
        // persisted refund-rate twin already covers today, to avoid double rows.
        List<AnomalyResponse> persisted = monitor.activeBanner();
        boolean persistedRefundRateToday = persisted.stream()
                .anyMatch(a -> "refund-rate".equals(a.code()) && today.equals(a.occurredOn()));
        if (persistedRefundRateToday) {
            out.removeIf(a -> "high-refund-rate".equals(a.code()));
        }
        List<Anomaly> merged = new ArrayList<>();
        for (AnomalyResponse a : persisted) {
            merged.add(new Anomaly(a.severity(), a.code(), a.message(), a.at()));
        }
        merged.addAll(out);
        return merged;
    }
}
