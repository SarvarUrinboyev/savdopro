package uz.barakat.market.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.market.domain.AuditEntry;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findByCreatedAtBetweenOrderByIdDesc(
            LocalDateTime from, LocalDateTime to, Pageable page);
}
