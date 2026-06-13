package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/** A shop's registered outbound-webhook endpoint + the events it wants. */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Filter(name = "accountFilter", condition = "shop_id IN (:shopIds)")
@Entity
@Table(name = "webhook_subscriptions")
@Getter
@Setter
public class WebhookSubscription extends TenantScopedEntity {

    /** HTTPS endpoint that receives the POSTed events. */
    @Column(nullable = false, length = 500)
    private String url;

    /** Per-subscription HMAC signing secret (returned to the owner once). */
    @Column(nullable = false, length = 80)
    private String secret;

    /** CSV of subscribed event types, e.g. {@code sale.created,stock.updated}. */
    @Column(nullable = false, length = 500)
    private String events;

    @Column(nullable = false)
    private boolean active = true;
}
