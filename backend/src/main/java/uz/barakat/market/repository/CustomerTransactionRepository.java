package uz.barakat.market.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.barakat.market.domain.CustomerTransaction;
import uz.barakat.market.domain.CustomerTxType;

public interface CustomerTransactionRepository
        extends JpaRepository<CustomerTransaction, Long> {

    /** A customer's ledger, newest line first. */
    List<CustomerTransaction> findByCustomerIdOrderByDateDescIdDesc(Long customerId);

    /** Customer transactions of a given type within a date range. */
    List<CustomerTransaction> findByTypeAndDateBetweenOrderByDateDescIdDesc(
            CustomerTxType type, LocalDate from, LocalDate to);

    /**
     * Per-customer ledger totals computed in the database. Lets the customer
     * list build every balance from a single GROUP BY instead of loading every
     * transaction row into memory — a shop with thousands of customers, each
     * with dozens of ledger lines, would otherwise pull hundreds of thousands
     * of rows on every list call. Tenant-scoped via the same Hibernate filter
     * as any other CustomerTransaction query.
     */
    @Query("""
            SELECT t.customerId AS customerId,
                   SUM(CASE WHEN t.type = :goods THEN t.amount ELSE 0 END) AS goods,
                   SUM(CASE WHEN t.type = :payment THEN t.amount ELSE 0 END) AS paid,
                   COUNT(t) AS txCount
            FROM CustomerTransaction t
            GROUP BY t.customerId
            """)
    List<LedgerTotals> aggregateLedgerTotals(@Param("goods") CustomerTxType goods,
                                             @Param("payment") CustomerTxType payment);

    /** Projection for {@link #aggregateLedgerTotals}. */
    interface LedgerTotals {
        Long getCustomerId();

        BigDecimal getGoods();

        BigDecimal getPaid();

        long getTxCount();
    }
}
