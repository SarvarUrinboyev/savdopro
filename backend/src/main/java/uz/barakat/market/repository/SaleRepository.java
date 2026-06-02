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
}
