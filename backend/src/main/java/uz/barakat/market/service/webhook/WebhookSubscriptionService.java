package uz.barakat.market.service.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.WebhookDelivery;
import uz.barakat.market.domain.WebhookSubscription;
import uz.barakat.market.dto.pub.PublicDtos.WebhookEnvelope;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.repository.WebhookDeliveryRepository;
import uz.barakat.market.repository.WebhookSubscriptionRepository;

/**
 * Manages a shop's webhook subscriptions and enqueues deliveries. Subscription
 * URLs are validated to be public HTTPS endpoints (SSRF guard). Enqueue runs in
 * its own transaction (called from the AFTER_COMMIT {@link WebhookEventListener}).
 */
@Service
public class WebhookSubscriptionService {

    /** Event types a subscription may listen to ({@code *} = all). */
    public static final Set<String> EVENT_TYPES = Set.of(
            "sale.created", "sale.refunded", "payment.recorded",
            "stock.updated", "product.created", "product.updated", "product.low_stock",
            "order.created", "order.status_changed");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookSubscriptionRepository subs;
    private final WebhookDeliveryRepository deliveries;
    private final ObjectMapper mapper;

    public WebhookSubscriptionService(WebhookSubscriptionRepository subs,
                                      WebhookDeliveryRepository deliveries, ObjectMapper mapper) {
        this.subs = subs;
        this.deliveries = deliveries;
        this.mapper = mapper;
    }

    public record CreatedSubscription(Long id, String url, String events, String secret) { }

    @Transactional
    public CreatedSubscription create(String url, List<String> events) {
        validateUrl(url);
        Set<String> wanted = new LinkedHashSet<>();
        if (events != null) {
            for (String e : events) {
                if (e == null) continue;
                String norm = e.trim().toLowerCase();
                if (!norm.isEmpty()) wanted.add(norm);
            }
        }
        if (wanted.isEmpty()) {
            throw new BadRequestException("Kamida bitta hodisa (event) tanlang");
        }
        for (String e : wanted) {
            if (!"*".equals(e) && !EVENT_TYPES.contains(e)) {
                throw new BadRequestException("Noma'lum hodisa: " + e);
            }
        }
        WebhookSubscription sub = new WebhookSubscription();
        sub.setUrl(url.strip());
        sub.setSecret(randomSecret());
        sub.setEvents(String.join(",", wanted));
        sub.setActive(true);
        WebhookSubscription saved = subs.save(sub);
        return new CreatedSubscription(saved.getId(), saved.getUrl(), saved.getEvents(), saved.getSecret());
    }

    @Transactional(readOnly = true)
    public List<WebhookSubscription> list() {
        return subs.findByOrderByCreatedAtDesc();
    }

    @Transactional
    public void delete(Long id) {
        WebhookSubscription sub = subs.findById(id)
                .orElseThrow(() -> new BadRequestException("Obuna topilmadi"));
        subs.delete(sub);
    }

    @Transactional(readOnly = true)
    public List<WebhookDelivery> recentDeliveries(int limit) {
        int size = Math.min(Math.max(limit, 1), 500);
        return deliveries.findByOrderByCreatedAtDescIdDesc(PageRequest.of(0, size));
    }

    /** Enqueues a test "ping" delivery to one subscription. */
    @Transactional
    public void test(Long id) {
        WebhookSubscription sub = subs.findById(id)
                .orElseThrow(() -> new BadRequestException("Obuna topilmadi"));
        WebhookEnvelope env = new WebhookEnvelope("ping", LocalDateTime.now(), sub.getShopId(),
                Map.of("message", "SavdoPRO webhook test"));
        WebhookDelivery d = new WebhookDelivery();
        d.setSubscriptionId(sub.getId());
        d.setEventType("ping");
        d.setPayloadJson(toJson(env));
        d.setStatus(WebhookDelivery.PENDING);
        d.setNextAttemptAt(LocalDateTime.now());
        deliveries.save(d);
    }

    /**
     * Fans an event out to the current shop's matching active subscriptions,
     * freezing the payload per delivery. Best-effort: never throws into the
     * AFTER_COMMIT listener.
     */
    @Transactional
    public void enqueue(String eventType, WebhookEnvelope envelope) {
        List<WebhookSubscription> active = subs.findByActiveTrue();
        if (active.isEmpty()) {
            return;
        }
        String body = toJson(envelope);
        for (WebhookSubscription sub : active) {
            if (!subscribes(sub, eventType)) {
                continue;
            }
            WebhookDelivery d = new WebhookDelivery();
            d.setSubscriptionId(sub.getId());
            d.setEventType(eventType);
            d.setPayloadJson(body);
            d.setStatus(WebhookDelivery.PENDING);
            d.setNextAttemptAt(LocalDateTime.now());
            deliveries.save(d);
        }
    }

    private static boolean subscribes(WebhookSubscription sub, String eventType) {
        if (sub.getEvents() == null) {
            return false;
        }
        for (String e : sub.getEvents().split(",")) {
            String t = e.trim();
            if (t.equals("*") || t.equalsIgnoreCase(eventType)) {
                return true;
            }
        }
        return false;
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception ex) {
            throw new IllegalStateException("Webhook payload serialize failed", ex);
        }
    }

    private static String randomSecret() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SSRF guard: the endpoint must be HTTPS and resolve only to public IPs —
     * blocks localhost, RFC1918, link-local, loopback, any-local and multicast,
     * so a subscription can't be used to probe the server's internal network.
     */
    private static void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new BadRequestException("Webhook URL kerak");
        }
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (RuntimeException ex) {
            throw new BadRequestException("Noto'g'ri URL");
        }
        if (uri.getScheme() == null || !uri.getScheme().equalsIgnoreCase("https")) {
            throw new BadRequestException("Webhook URL https bo'lishi kerak");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank() || host.equalsIgnoreCase("localhost")) {
            throw new BadRequestException("Webhook URL ichki/lokal manzilga ruxsat etilmaydi");
        }
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
                        || addr.isMulticastAddress()) {
                    throw new BadRequestException("Webhook URL ichki/lokal manzilga ruxsat etilmaydi");
                }
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("Webhook URL hostini aniqlab bo'lmadi");
        }
    }

    public List<String> eventsOf(WebhookSubscription sub) {
        if (sub.getEvents() == null || sub.getEvents().isBlank()) {
            return List.of();
        }
        return Arrays.stream(sub.getEvents().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
