package uz.barakat.license.repository;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.license.domain.DunningLog;

public interface DunningLogRepository extends JpaRepository<DunningLog, Long> {

    boolean existsByAccountIdAndMilestoneAndExpiryDate(
            Long accountId, String milestone, LocalDate expiryDate);
}
