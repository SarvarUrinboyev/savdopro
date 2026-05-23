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

/** A home / personal expense, tracked separately from the market. */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Entity
@Table(name = "home_expenses")
@Getter
@Setter
public class HomeExpense extends TenantScopedEntity {

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private PaymentType paymentType;

    @Column(name = "cash_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal cashAmount = BigDecimal.ZERO;

    @Column(name = "naqd_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal naqdAmount = BigDecimal.ZERO;

    @Column(name = "card_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal cardAmount = BigDecimal.ZERO;

    /** Currency this expense was entered in (UZS by default). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency = Currency.UZS;

    @Column(length = 500)
    private String note;
}
