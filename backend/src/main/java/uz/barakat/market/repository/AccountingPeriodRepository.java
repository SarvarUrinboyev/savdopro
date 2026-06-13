package uz.barakat.market.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.barakat.market.domain.AccountingPeriod;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {

    List<AccountingPeriod> findAllByOrderByPeriodStartDesc();

    /**
     * CLOSED periods whose date range contains {@code date}. A non-empty result
     * means {@code date} is locked and no entry may touch it.
     */
    @Query("SELECT p FROM AccountingPeriod p "
            + "WHERE p.status = uz.barakat.market.domain.PeriodStatus.CLOSED "
            + "  AND p.periodStart <= :date AND p.periodEnd >= :date")
    List<AccountingPeriod> findClosedCovering(@Param("date") LocalDate date);
}
