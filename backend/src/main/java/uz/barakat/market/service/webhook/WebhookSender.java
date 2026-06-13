package uz.barakat.market.service.webhook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.barakat.market.config.WebhookProperties;

/**
 * Posts a signed webhook delivery over HTTP. Mirrors {@code TelegramBotApi}'s
 * {@link HttpClient} usage. Never throws — returns a {@link Result} the
 * dispatcher records. The HTTP call happens OUTSIDE any DB transaction.
 */
@Component
public class WebhookSender {

    private static final Logger log = LoggerFactory.getLogger(WebhookSender.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER) // never follow redirects (SSRF hardening)
            .build();
    private final WebhookProperties props;

    public WebhookSender(WebhookProperties props) {
        this.props = props;
    }

    public record Result(boolean success, int status, String error) { }

    public Result send(String url, String eventType, Long deliveryId, String signature, String body) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(props.timeoutSeconds()))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "SavdoPRO-Webhook/1")
                    .header("X-Barakat-Event", eventType)
                    .header("X-Barakat-Delivery-Id", String.valueOf(deliveryId))
                    .header("X-Barakat-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                    .header("X-Barakat-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            int sc = resp.statusCode();
            if (sc / 100 == 2) {
                return new Result(true, sc, null);
            }
            return new Result(false, sc, "HTTP " + sc);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(false, 0, "interrupted");
        } catch (Exception ex) {
            log.debug("Webhook POST to {} failed: {}", url, ex.toString());
            return new Result(false, 0, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }
}
