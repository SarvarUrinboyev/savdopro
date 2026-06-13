package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/**
 * One outbound webhook attempt record — the durable delivery outbox. One row
 * per (event × subscription). The {@code payloadJson} is frozen at enqueue
 * time so retries are byte-identical and the HMAC signature stays stable.
 */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Filter(name = "accountFilter", condition = "shop_id IN (:shopIds)")
@Entity
@Table(name = "webhook_deliveries")
@Getter
@Setter
public class WebhookDelivery extends TenantScopedEntity {

    public static final String PENDING = "PENDING";
    public static final String DELIVERED = "DELIVERED";
    public static final String FAILED = "FAILED";

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false, length = 12)
    private String status = PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt = LocalDateTime.now();

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
}
