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

    // ----- Payme (Paycom) transaction state machine -----
    // Payme's Merchant API tracks a transaction separately from our payment:
    // it is Created (state 1), then Performed (2) or Cancelled (-1 before /
    // -2 after perform). These mirror that state so CreateTransaction /
    // PerformTransaction / CancelTransaction / CheckTransaction are idempotent.
    // Null on payments that never went through Payme (Click / MANUAL).

    /** Payme's transaction id (params.id), unique per Payme transaction. */
    @Column(name = "payme_tx_id", length = 120)
    private String paymeTxId;

    /** 1=created, 2=performed, -1=cancelled (pre-perform), -2=cancelled (post-perform). */
    @Column(name = "payme_state")
    private Integer paymeState;

    @Column(name = "payme_create_time")
    private Long paymeCreateTime;

    @Column(name = "payme_perform_time")
    private Long paymePerformTime;

    @Column(name = "payme_cancel_time")
    private Long paymeCancelTime;

    @Column(name = "payme_reason")
    private Integer paymeReason;
}
