package uz.barakat.market.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.barakat.market.domain.StockMovement;
import uz.barakat.market.domain.StockReason;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /** The 20 most recent movements for a product, newest first. */
    List<StockMovement> findTop20ByProductIdOrderByIdDesc(Long productId);

    /** Movements with the given reason inside a timestamp window (Management sales). */
    List<StockMovement> findByReasonAndCreatedAtBetween(
            StockReason reason, LocalDateTime from, LocalDateTime to);

    /**
     * Per-product sold quantity inside the window. Returns rows of
     * (productId, soldQty). Negative deltas (sales) are SUMmed and
     * negated so the value is a positive sold count. Used by the
     * "Mahsulot bo'yicha foyda" report; service joins with products
     * to compute revenue + profit.
     */
    @Query(value =
            "SELECT m.product_id AS pid, -SUM(m.delta) AS qty "
            + "FROM stock_movements m "
            + "WHERE m.reason = 'SALE' "
            + "  AND m.shop_id IN (:shopIds) "
            + "  AND m.created_at >= :from AND m.created_at < :to "
            + "GROUP BY m.product_id",
            nativeQuery = true)
    List<Object[]> sumSalesQtyByProduct(
            @Param("shopIds") Collection<Long> shopIds,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Per-product sold qty + revenue + cost over the window, valued at the
     * price snapshot recorded on each movement, falling back to the product's
     * CURRENT price for legacy (pre-V23) rows via COALESCE. Returns rows of
     * (productId, qty, revenueUzs, costUzs). Scoped to the active shop(s).
     */
    @Query(value =
            "SELECT m.product_id AS pid, "
            + "       -SUM(m.delta) AS qty, "
            + "       SUM(-m.delta * COALESCE(m.unit_sale_price, p.sale_price)) AS revenue, "
            + "       SUM(-m.delta * COALESCE(m.unit_cost_price, p.purchase_price)) AS cost "
            + "FROM stock_movements m "
            + "JOIN products p ON p.id = m.product_id "
            + "WHERE m.reason = 'SALE' "
            + "  AND m.shop_id IN (:shopIds) "
            + "  AND m.created_at >= :from AND m.created_at < :to "
            + "GROUP BY m.product_id",
            nativeQuery = true)
    List<Object[]> salesProfitByProduct(
            @Param("shopIds") Collection<Long> shopIds,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Hourly sales totals (count of SALE movements) for the window.
     * Returns rows of (hourOfDay 0..23, count) — fuels the "Soatlik
     * sotuvlar" heatmap on the Reports page.
     */
    @Query(value =
            "SELECT EXTRACT(HOUR FROM m.created_at) AS h, COUNT(*) AS c "
            + "FROM stock_movements m "
            + "WHERE m.reason = 'SALE' "
            + "  AND m.shop_id IN (:shopIds) "
            + "  AND m.created_at >= :from AND m.created_at < :to "
            + "GROUP BY EXTRACT(HOUR FROM m.created_at)",
            nativeQuery = true)
    List<Object[]> hourlySalesCount(
            @Param("shopIds") Collection<Long> shopIds,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
