package uz.barakat.license.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * A subscription payment. Created {@code PENDING} when a merchant starts
 * checkout; flipped to {@code PAID} by the PSP webhook, which then extends
 * the account's subscription. PSP-specific signature verification lives in
 * the webhook adapter, not here.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionPlan plan;

    @Column(name = "amount_uzs", nullable = false)
    private long amountUzs;

    @Column(nullable = false)
    private int months;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status = PaymentStatus.PENDING;

    /** PAYME / CLICK / MANUAL. */
    @Column(length = 20)
    private String provider;

    /** PSP transaction id, set when the payment is confirmed. */
    @Column(name = "external_id", length = 120)
    private String externalId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
