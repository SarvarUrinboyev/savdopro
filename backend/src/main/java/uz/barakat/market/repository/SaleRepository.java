package uz.barakat.market.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.barakat.market.domain.Sale;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    /** A page of sales (newest first). Spring Data {@code Slice} fetches
     *  {@code size + 1} rows so {@code hasNext()} works without a COUNT
     *  query — ideal for "load more". */
    Slice<Sale> findByOrderByCreatedAtDescIdDesc(Pageable pageable);

    /** Idempotency lookup for offline checkout replay (tenant-scoped by filter). */
    Optional<Sale> findFirstByClientRef(String clientRef);

    /** Sales window — used by Reports and end-of-day. */
    List<Sale> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);

    /**
     * Single-shot summary of a window: (count, sumTotal, sumRefunded).
     * Cheap enough to call multiple times per request — backs the AI
     * snapshot which now bundles today/yesterday/week/month totals.
     */
    @Query("SELECT COUNT(s), COALESCE(SUM(s.totalUzs), 0), COALESCE(SUM(s.refundedTotalUzs), 0) "
            + "FROM Sale s WHERE s.createdAt >= :from AND s.createdAt < :to")
    Object[] summaryBetween(@Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to);

    /** Per-cashier performance for a window: receipts + net takings. */
    @Query("SELECT s.cashier AS cashier, COUNT(s) AS receipts, "
            + "COALESCE(SUM(s.totalUzs - s.refundedTotalUzs), 0) AS net "
            + "FROM Sale s WHERE s.createdAt >= :from AND s.createdAt < :to "
            + "GROUP BY s.cashier ORDER BY net DESC")
    List<CashierStatRow> cashierStats(@Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    interface CashierStatRow {
        String getCashier();
        long getReceipts();
        java.math.BigDecimal getNet();
    }

    /** Per-cashier gross + refunded totals for a window — backs the refund-rate
     *  outlier check. Gross is takings before refunds; refunded is the cumulative
     *  refund amount on sales rung up in the window. */
    @Query("SELECT s.cashier AS cashier, COUNT(s) AS receipts, "
            + "COALESCE(SUM(s.totalUzs), 0) AS gross, "
            + "COALESCE(SUM(s.refundedTotalUzs), 0) AS refunded "
            + "FROM Sale s WHERE s.createdAt >= :from AND s.createdAt < :to "
            + "GROUP BY s.cashier")
    List<CashierRefundRow> cashierRefundStats(@Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    interface CashierRefundRow {
        String getCashier();
        long getReceipts();
        java.math.BigDecimal getGross();
        java.math.BigDecimal getRefunded();
    }

    /** Sales whose most-recent refund fell in the window (ordered by refund time).
     *  Backs the refund-burst, large-refund and stale-refund checks. Note
     *  {@code refundedAt} is overwritten on each partial refund, so a sale
     *  refunded several times appears once at its latest refund time. */
    @Query("SELECT s FROM Sale s WHERE s.refundedAt >= :from AND s.refundedAt < :to "
            + "ORDER BY s.refundedAt ASC")
    List<Sale> findRefundedBetween(@Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);
}
