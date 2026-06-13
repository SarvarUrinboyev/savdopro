package uz.barakat.market.service.webhook;

/**
 * Domain events that map to webhook event types but have no existing
 * {@code LedgerEvents} counterpart. Sale / refund / payment / stock reuse
 * {@code LedgerEvents}; product and (supplier) order changes are published here
 * by {@code ProductService} / {@code OrderService}. Consumed AFTER_COMMIT by
 * {@link WebhookEventListener}.
 */
public final class WebhookEvents {

    private WebhookEvents() {
    }

    /** A product was created or updated. {@code type} = "created" | "updated". */
    public record ProductChanged(Long productId, String type) {
    }

    /** A supplier order changed. {@code status} = "created" | "status_changed". */
    public record OrderChanged(Long orderId, String status) {
    }
}
