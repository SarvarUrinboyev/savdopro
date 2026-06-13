package uz.barakat.market.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.market.domain.AnomalyAlert;

public interface AnomalyAlertRepository extends JpaRepository<AnomalyAlert, Long> {

    /**
     * Dedupe lookup. {@code shopId} is explicit because the scheduled scan
     * runs with the tenant scope set to the shop being scanned, but we want
     * the dedupe pinned to that exact shop regardless of filter state.
     */
    Optional<AnomalyAlert> findFirstByShopIdAndDedupeKey(Long shopId, String dedupeKey);

    /** History list for the in-app page (newest first), windowed by business day. */
    List<AnomalyAlert> findByOccurredOnBetweenOrderByCreatedAtDesc(
            LocalDate from, LocalDate to, Pageable page);

    /** Banner feed: unacknowledged alerts from {@code from} onward, newest first. */
    List<AnomalyAlert> findByAcknowledgedFalseAndOccurredOnGreaterThanEqualOrderByCreatedAtDesc(
            LocalDate from);

    /** Count of open (unacknowledged) alerts from {@code from} onward — dashboard badge. */
    long countByAcknowledgedFalseAndOccurredOnGreaterThanEqual(LocalDate from);
}
