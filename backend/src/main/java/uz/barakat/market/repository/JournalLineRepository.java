package uz.barakat.market.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.barakat.market.domain.JournalLine;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    /** Guards account deletion — an account with postings cannot be removed. */
    boolean existsByAccountId(Long accountId);

    /**
     * Trial-balance aggregation: per-account debit + credit totals over the
     * posted entries whose {@code entry_date} falls in [from, to]. Returns rows
     * of (accountId, totalDebit, totalCredit). Native + explicitly shop-scoped
     * because the Hibernate {@code @Filter} does not rewrite native SQL.
     *
     * <p>For a balance-sheet "as of" snapshot pass a very early {@code from};
     * for a P&L pass the period start.
     */
    @Query(value =
            "SELECT l.account_id AS aid, "
            + "       COALESCE(SUM(l.debit), 0)  AS dr, "
            + "       COALESCE(SUM(l.credit), 0) AS cr "
            + "FROM gl_journal_line l "
            + "JOIN gl_journal_entry e ON e.id = l.entry_id "
            + "WHERE l.shop_id IN (:shopIds) "
            + "  AND e.posted = TRUE "
            + "  AND e.entry_date >= :from AND e.entry_date <= :to "
            + "GROUP BY l.account_id",
            nativeQuery = true)
    List<Object[]> aggregateByAccount(
            @Param("shopIds") Collection<Long> shopIds,
            @Param("from") LocalDate from, @Param("to") LocalDate to);
}
