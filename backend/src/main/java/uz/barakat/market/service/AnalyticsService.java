package uz.barakat.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * Cross-product analytics that don't fit one of the existing services.
 *
 * <ul>
 *   <li>{@code profitByProduct(from, to)} — per-SKU revenue + profit
 *       inside the window, sorted by profit desc. Used by the Reports
 *       page "Mahsulot bo'yicha foyda" tab.</li>
 *   <li>{@code hourlySales(from, to)} — 24-bucket count of SALE
 *       movements, indexed by hour-of-day. Powers the heatmap.</li>
 * </ul>
 *
 * Both reuse the {@code stock_movements} table with {@code reason = SALE},
 * which is the canonical sale ledger in this schema.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final StockMovementRepository movements;
    private final ProductRepository products;

    public AnalyticsService(StockMovementRepository movements, ProductRepository products) {
        this.movements = movements;
        this.products = products;
    }

    public record ProductProfitRow(
            Long productId,
            String name,
            int soldQty,
            BigDecimal revenueUsd,
            BigDecimal costUsd,
            BigDecimal profitUsd,
            BigDecimal marginPercent) { }

    public record HourlySalesBucket(int hour, long count) { }

    /** Per-product profit summary over [from, to). Sorted profit desc. */
    public List<ProductProfitRow> profitByProduct(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = (from == null ? LocalDate.now().minusDays(30) : from).atStartOfDay();
        LocalDateTime toDt = (to == null ? LocalDate.now() : to).plusDays(1).atStartOfDay();

        // Pre-load every referenced product in one trip so we don't N+1.
        List<Long> scope = TenantContext.activeScope();
        if (scope.isEmpty()) return List.of();
        List<Object[]> raw = movements.sumSalesQtyByProduct(scope, fromDt, toDt);
        if (raw.isEmpty()) return List.of();

        Map<Long, Product> byId = new HashMap<>();
        for (Product p : products.findAll()) byId.put(p.getId(), p);

        List<ProductProfitRow> rows = new ArrayList<>(raw.size());
        for (Object[] r : raw) {
            Long pid = ((Number) r[0]).longValue();
            int qty = ((Number) r[1]).intValue();
            Product p = byId.get(pid);
            if (p == null) continue;
            BigDecimal sell = nullSafe(p.getSalePrice());
            BigDecimal cost = nullSafe(p.getPurchasePrice());
            BigDecimal qtyDec = BigDecimal.valueOf(qty);
            BigDecimal revenue = sell.multiply(qtyDec).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalCost = cost.multiply(qtyDec).setScale(2, RoundingMode.HALF_UP);
            BigDecimal profit = revenue.subtract(totalCost);
            BigDecimal margin = revenue.signum() == 0
                    ? BigDecimal.ZERO
                    : profit.multiply(BigDecimal.valueOf(100))
                            .divide(revenue, 1, RoundingMode.HALF_UP);
            rows.add(new ProductProfitRow(
                    pid, p.getName(), qty, revenue, totalCost, profit, margin));
        }
        rows.sort(Comparator.comparing(ProductProfitRow::profitUsd).reversed());
        return rows;
    }

    /** 24 hourly buckets (0–23) of SALE-movement counts over [from, to). */
    public List<HourlySalesBucket> hourlySales(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = (from == null ? LocalDate.now().minusDays(30) : from).atStartOfDay();
        LocalDateTime toDt = (to == null ? LocalDate.now() : to).plusDays(1).atStartOfDay();

        long[] hours = new long[24];
        List<Long> scope = TenantContext.activeScope();
        if (!scope.isEmpty()) {
            for (Object[] r : movements.hourlySalesCount(scope, fromDt, toDt)) {
                int h = ((Number) r[0]).intValue();
                long c = ((Number) r[1]).longValue();
                if (h >= 0 && h < 24) hours[h] += c;
            }
        }
        List<HourlySalesBucket> out = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) out.add(new HourlySalesBucket(h, hours[h]));
        return out;
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
