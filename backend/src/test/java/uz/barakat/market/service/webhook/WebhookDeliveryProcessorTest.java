package uz.barakat.market.service.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uz.barakat.market.config.WebhookProperties;
import uz.barakat.market.domain.WebhookDelivery;
import uz.barakat.market.domain.WebhookSubscription;
import uz.barakat.market.repository.WebhookDeliveryRepository;
import uz.barakat.market.repository.WebhookSubscriptionRepository;
import uz.barakat.market.service.webhook.WebhookDeliveryProcessor.Sendable;

/** Delivery outcome recording (retry/backoff/fail) and orphan handling. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookDeliveryProcessorTest {

    @Mock private WebhookDeliveryRepository deliveries;
    @Mock private WebhookSubscriptionRepository subs;

    private final WebhookProperties props = new WebhookProperties(true, 3, 10, 50, 30);
    private WebhookDeliveryProcessor proc;

    @BeforeEach
    void setup() {
        proc = new WebhookDeliveryProcessor(deliveries, subs, props);
    }

    private static WebhookDelivery delivery(int attempts) {
        WebhookDelivery d = new WebhookDelivery();
        d.setId(1L);
        d.setSubscriptionId(10L);
        d.setStatus(WebhookDelivery.PENDING);
        d.setEventType("sale.created");
        d.setPayloadJson("{}");
        d.setAttempts(attempts);
        d.setNextAttemptAt(LocalDateTime.now());
        return d;
    }

    @Test
    void recordSuccessMarksDelivered() {
        WebhookDelivery d = delivery(0);
        when(deliveries.findById(1L)).thenReturn(Optional.of(d));
        proc.record(1L, true, null);
        assertThat(d.getStatus()).isEqualTo(WebhookDelivery.DELIVERED);
        assertThat(d.getDeliveredAt()).isNotNull();
        assertThat(d.getAttempts()).isEqualTo(1);
    }

    @Test
    void recordFailureRetriesWithBackoffUnderMax() {
        WebhookDelivery d = delivery(0);
        when(deliveries.findById(1L)).thenReturn(Optional.of(d));
        proc.record(1L, false, "boom");
        assertThat(d.getStatus()).isEqualTo(WebhookDelivery.PENDING);
        assertThat(d.getAttempts()).isEqualTo(1);
        assertThat(d.getLastError()).isEqualTo("boom");
        assertThat(d.getNextAttemptAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void recordFailureAtMaxAttemptsMarksFailed() {
        WebhookDelivery d = delivery(2); // becomes 3 == maxAttempts
        when(deliveries.findById(1L)).thenReturn(Optional.of(d));
        proc.record(1L, false, "still failing");
        assertThat(d.getStatus()).isEqualTo(WebhookDelivery.FAILED);
        assertThat(d.getAttempts()).isEqualTo(3);
    }

    @Test
    void claimDueReturnsSendablesAndFailsOrphans() {
        WebhookDelivery good = delivery(0);
        good.setId(1L);
        good.setSubscriptionId(10L);
        WebhookDelivery orphan = delivery(0);
        orphan.setId(2L);
        orphan.setSubscriptionId(20L);
        when(deliveries.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(any(), any(), any()))
                .thenReturn(List.of(good, orphan));
        WebhookSubscription active = new WebhookSubscription();
        active.setActive(true);
        active.setUrl("https://8.8.8.8/hook");
        active.setSecret("whsec_x");
        when(subs.findById(10L)).thenReturn(Optional.of(active));
        when(subs.findById(20L)).thenReturn(Optional.empty());

        List<Sendable> out = proc.claimDue(50);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).deliveryId()).isEqualTo(1L);
        assertThat(out.get(0).url()).isEqualTo("https://8.8.8.8/hook");
        assertThat(orphan.getStatus()).isEqualTo(WebhookDelivery.FAILED);
    }
}
