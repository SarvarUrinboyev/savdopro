package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * An online debt repayment via Click or Payme. Deliberately NOT
 * tenant-filtered: the provider's servers call our webhooks with no shop
 * context, so a row is addressed by its absolute id and merely records
 * {@code shopId} for reporting. Persisting every attempt makes the
 * provider callbacks idempotent (a repeated PerformTransaction returns the
 * same result instead of crediting the debt twice).
 *
 * <p>{@code state}: 0 = created/prepared, 2 = performed (paid),
 * -1 = cancelled before perform, -2 = cancelled after perform.</p>
 */
@Entity
@Table(name = "online_payments")
@Getter
@Setter
public class OnlinePayment extends BaseEntity {

    public static final int STATE_CREATED = 0;
    public static final int STATE_PERFORMED = 2;
    public static final int STATE_CANCELLED = -1;
    public static final int STATE_CANCELLED_AFTER_PERFORM = -2;

    @Column(nullable = false, length = 16)
    private String provider;

    @Column(name = "provider_txn_id", length = 64)
    private String providerTxnId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "shop_id")
    private Long shopId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false)
    private int state = STATE_CREATED;

    @Column(name = "create_time_ms")
    private Long createTimeMs;

    @Column(name = "perform_time_ms")
    private Long performTimeMs;

    @Column(name = "cancel_time_ms")
    private Long cancelTimeMs;

    @Column(name = "reason")
    private Integer reason;

    @Column(name = "ledger_tx_id")
    private Long ledgerTxId;
}
