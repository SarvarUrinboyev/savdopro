package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/**
 * One line of a customer's ledger: either goods handed over or a
 * payment received. The running balance is sum(GOODS) - sum(PAYMENT).
 */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Filter(name = "accountFilter", condition = "shop_id IN (:shopIds)")
@Entity
@Table(name = "customer_transactions")
@Getter
@Setter
public class CustomerTransaction extends TenantScopedEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerTxType type;

    /** Product name for GOODS, free text for PAYMENT; optional. */
    @Column(length = 255)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String note;

    /**
     * Signed loyalty-points delta for this row (Phase 4.4). Positive on
     * earn rows, negative on redeem rows, null on legacy rows that
     * predate loyalty. {@code sum(points_delta)} is the source of
     * truth; {@code customers.points_balance} is just a denormalised cache.
     */
    @Column(name = "points_delta")
    private Long pointsDelta;
}
