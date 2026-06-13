package uz.barakat.market.service.webhook;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.config.WebhookProperties;
import uz.barakat.market.domain.WebhookDelivery;
import uz.barakat.market.domain.WebhookSubscription;
import uz.barakat.market.repository.WebhookDeliveryRepository;
import uz.barakat.market.repository.WebhookSubscriptionRepository;

/**
 * Transactional helper for the {@link WebhookDispatcher}. Split into its own
 * bean so the read (claim) and write (record) steps run in real, separate
 * transactions while the HTTP send happens between them, OUTSIDE any tx.
 */
@Service
public class WebhookDeliveryProcessor {

    private final WebhookDeliveryRepository deliveries;
    private final WebhookSubscriptionRepository subs;
    private final WebhookProperties props;

    public WebhookDeliveryProcessor(WebhookDeliveryRepository deliveries,
                                    WebhookSubscriptionRepository subs, WebhookProperties props) {
        this.deliveries = deliveries;
        this.subs = subs;
        this.props = props;
    }

    /** A delivery ready to send, joined with its subscription's url + secret. */
    public record Sendable(Long deliveryId, String url, String secret,
                           String eventType, String payloadJson) { }

    /**
     * Loads due deliveries, resolving each to its subscription. Orphaned ones
     * (subscription deleted/disabled) are marked FAILED here; the rest are
     * returned for sending. Runs under GlobalScope (all shops).
     */
    @Transactional
    public List<Sendable> claimDue(int batchSize) {
        List<WebhookDelivery> due = deliveries.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                WebhookDelivery.PENDING, LocalDateTime.now(), PageRequest.of(0, batchSize));
        List<Sendable> out = new ArrayList<>();
        for (WebhookDelivery d : due) {
            WebhookSubscription sub = subs.findById(d.getSubscriptionId()).orElse(null);
            if (sub == null || !sub.isActive()) {
                d.setStatus(WebhookDelivery.FAILED);
                d.setLastError("obuna o'chirilgan yoki topilmadi");
                continue; // dirty-checked, persisted on commit
            }
            out.add(new Sendable(d.getId(), sub.getUrl(), sub.getSecret(),
                    d.getEventType(), d.getPayloadJson()));
        }
        return out;
    }

    /** Records the outcome of one send: DELIVERED, or retry with backoff / FAILED. */
    @Transactional
    public void record(Long deliveryId, boolean success, String error) {
        deliveries.findById(deliveryId).ifPresent(d -> {
            d.setAttempts(d.getAttempts() + 1);
            if (success) {
                d.setStatus(WebhookDelivery.DELIVERED);
                d.setDeliveredAt(LocalDateTime.now());
                d.setLastError(null);
            } else {
                d.setLastError(truncate(error));
                if (d.getAttempts() >= props.maxAttempts()) {
                    d.setStatus(WebhookDelivery.FAILED);
                } else {
                    d.setStatus(WebhookDelivery.PENDING);
                    d.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoffSeconds(d.getAttempts())));
                }
            }
        });
    }

    private long backoffSeconds(int attempts) {
        int shift = Math.min(Math.max(attempts - 1, 0), 12);
        return (long) props.backoffBaseSeconds() * (1L << shift);
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
