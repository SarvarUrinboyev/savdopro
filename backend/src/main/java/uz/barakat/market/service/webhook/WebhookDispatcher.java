package uz.barakat.market.service.webhook;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.barakat.market.config.WebhookProperties;
import uz.barakat.market.service.GlobalScope;
import uz.barakat.market.service.webhook.WebhookDeliveryProcessor.Sendable;

/**
 * Drains the webhook delivery outbox on a schedule. Reuses {@code @EnableScheduling}
 * (no async infra). Runs under {@link GlobalScope} so it processes every shop's
 * due deliveries in one pass. The HTTP send happens between the claim and record
 * transactions — never holding a DB transaction open during network I/O.
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final WebhookDeliveryProcessor processor;
    private final WebhookSender sender;
    private final WebhookProperties props;
    private final GlobalScope globalScope;

    public WebhookDispatcher(WebhookDeliveryProcessor processor, WebhookSender sender,
                             WebhookProperties props, GlobalScope globalScope) {
        this.processor = processor;
        this.sender = sender;
        this.props = props;
        this.globalScope = globalScope;
    }

    @Scheduled(cron = "${webhook.dispatch-cron}")
    public void dispatch() {
        if (!props.enabled()) {
            return;
        }
        try {
            globalScope.run(this::drainOnce);
        } catch (RuntimeException ex) {
            log.warn("Webhook dispatch tick failed: {}", ex.toString());
        }
    }

    private void drainOnce() {
        List<Sendable> batch = processor.claimDue(props.batchSize());
        if (batch.isEmpty()) {
            return;
        }
        int delivered = 0;
        for (Sendable s : batch) {
            String signature = WebhookSigner.sign(s.secret(), s.payloadJson());
            WebhookSender.Result r = sender.send(
                    s.url(), s.eventType(), s.deliveryId(), signature, s.payloadJson());
            processor.record(s.deliveryId(), r.success(),
                    r.success() ? null : ("status=" + r.status() + " " + r.error()));
            if (r.success()) {
                delivered++;
            }
        }
        log.info("Webhook dispatch: {} sent, {} delivered", batch.size(), delivered);
    }
}
