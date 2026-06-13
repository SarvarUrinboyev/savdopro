package uz.barakat.market.service.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uz.barakat.market.domain.WebhookDelivery;
import uz.barakat.market.domain.WebhookSubscription;
import uz.barakat.market.dto.pub.PublicDtos.WebhookEnvelope;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.repository.WebhookDeliveryRepository;
import uz.barakat.market.repository.WebhookSubscriptionRepository;

/** SSRF URL validation + event fan-out for webhook subscriptions. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookSubscriptionServiceTest {

    @Mock private WebhookSubscriptionRepository subs;
    @Mock private WebhookDeliveryRepository deliveries;

    private WebhookSubscriptionService svc;

    @BeforeEach
    void setup() {
        svc = new WebhookSubscriptionService(subs, deliveries, new ObjectMapper().findAndRegisterModules());
        when(subs.save(any(WebhookSubscription.class))).thenAnswer(inv -> {
            WebhookSubscription s = inv.getArgument(0);
            s.setId(99L);
            return s;
        });
    }

    @Test
    void rejectsNonHttps() {
        assertThatThrownBy(() -> svc.create("http://example.com/hook", List.of("sale.created")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsLocalhost() {
        assertThatThrownBy(() -> svc.create("https://localhost/hook", List.of("sale.created")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsPrivateIp() {
        assertThatThrownBy(() -> svc.create("https://10.0.0.5/hook", List.of("sale.created")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> svc.create("https://127.0.0.1/hook", List.of("sale.created")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsUnknownEvent() {
        assertThatThrownBy(() -> svc.create("https://8.8.8.8/hook", List.of("bogus.event")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void acceptsPublicHttpsAndGeneratesSecret() {
        WebhookSubscriptionService.CreatedSubscription r =
                svc.create("https://8.8.8.8/hook", List.of("sale.created"));
        assertThat(r.secret()).startsWith("whsec_");
        assertThat(r.events()).contains("sale.created");
    }

    @Test
    void enqueueFansOutToMatchingSubscriptionsOnly() {
        WebhookSubscription a = sub(1L, "sale.created");
        WebhookSubscription b = sub(2L, "stock.updated");
        when(subs.findByActiveTrue()).thenReturn(List.of(a, b));

        svc.enqueue("sale.created", new WebhookEnvelope(
                "sale.created", LocalDateTime.now(), 5L, Map.of("k", "v")));

        ArgumentCaptor<WebhookDelivery> cap = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveries, times(1)).save(cap.capture());
        assertThat(cap.getValue().getEventType()).isEqualTo("sale.created");
        assertThat(cap.getValue().getSubscriptionId()).isEqualTo(1L);
        assertThat(cap.getValue().getStatus()).isEqualTo(WebhookDelivery.PENDING);
    }

    @Test
    void enqueueSkipsWhenNoSubscriptionMatches() {
        when(subs.findByActiveTrue()).thenReturn(List.of(sub(2L, "stock.updated")));
        svc.enqueue("sale.created", new WebhookEnvelope(
                "sale.created", LocalDateTime.now(), 5L, Map.of("k", "v")));
        verify(deliveries, never()).save(any());
    }

    private static WebhookSubscription sub(Long id, String events) {
        WebhookSubscription s = new WebhookSubscription();
        s.setId(id);
        s.setUrl("https://8.8.8.8/hook");
        s.setSecret("whsec_test");
        s.setEvents(events);
        s.setActive(true);
        return s;
    }
}
