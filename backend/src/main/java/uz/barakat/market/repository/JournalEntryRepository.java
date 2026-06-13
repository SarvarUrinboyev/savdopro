package uz.barakat.market.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.market.domain.JournalEntry;
import uz.barakat.market.domain.JournalSource;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    List<JournalEntry> findByEntryDateBetweenOrderByEntryDateDescIdDesc(
            LocalDate from, LocalDate to);

    /** Idempotency guard for auto-posting: has this source row already posted? */
    boolean existsBySourceAndSourceRef(JournalSource source, String sourceRef);

    /**
     * True if ANY entry of this source has a ref under the given prefix — used
     * by the backfill to skip a sale whose refund was already live-posted
     * (live refs are "SR:&lt;saleId&gt;:&lt;millis&gt;", backfill uses "SR:&lt;saleId&gt;").
     */
    boolean existsBySourceAndSourceRefStartingWith(JournalSource source, String prefix);

    Optional<JournalEntry> findFirstBySourceAndSourceRef(JournalSource source, String sourceRef);
}
