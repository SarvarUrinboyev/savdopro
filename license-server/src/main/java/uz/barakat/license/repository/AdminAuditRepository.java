package uz.barakat.license.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.license.domain.AdminAuditEntry;

public interface AdminAuditRepository extends JpaRepository<AdminAuditEntry, Long> {

    /** Newest first, paginated — used by the browser admin panel. */
    Page<AdminAuditEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
