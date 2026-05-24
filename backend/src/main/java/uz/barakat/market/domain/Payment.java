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
 * One entry of the payment journal ("To'lovlar jurnali"): every money
 * movement - customer payments, supplier payments, salaries, taxes.
 */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Filter(name = "accountFilter", condition = "shop_id IN (:shopIds)")
@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment extends TenantScopedEntity {

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentCategory category;

    /** Who the money came from or went to; optional. */
    @Column(length = 255)
    private String party;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** How the money moved: reuses NAQD / KASSA / KARTA. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentType method;

    /** Currency this payment was entered in (UZS by default). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency = Currency.UZS;

    @Column(length = 500)
    private String note;

    /**
     * Discount applied to this sale (Phase 4.4). Both fields are
     * metadata for the receipt + audit trail — the journal {@code amount}
     * column already stores the NET (post-discount) figure so historical
     * reports keep working. When both are non-zero the percent is
     * applied first, then the flat amount subtracted.
     */
    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    /**
     * Loyalty customer the sale was attributed to. Null on impersonal
     * walk-in sales. When set, {@code PaymentService.create} awards
     * points based on {@code amount} and credits {@code customers.points_balance}.
     */
    @Column(name = "customer_id")
    private Long customerId;
}
