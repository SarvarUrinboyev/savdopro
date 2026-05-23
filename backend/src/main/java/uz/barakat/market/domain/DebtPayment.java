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
 * One row in a debt's history - either a payment towards it or an
 * increase of it. Exactly one of {@code debtorId} / {@code customerDebtId}
 * is set (enforced by a database CHECK constraint).
 */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Entity
@Table(name = "debt_payments")
@Getter
@Setter
public class DebtPayment extends TenantScopedEntity {

    @Column(name = "debtor_id")
    private Long debtorId;

    @Column(name = "customer_debt_id")
    private Long customerDebtId;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private DebtEntryType entryType = DebtEntryType.PAYMENT;

    @Column(length = 500)
    private String note;
}
