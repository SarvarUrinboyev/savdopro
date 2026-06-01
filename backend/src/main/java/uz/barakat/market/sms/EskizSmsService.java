package uz.barakat.market.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Eskiz.uz SMS gateway client. Logs in once, caches the bearer token and
 * re-authenticates on a 401. Every failure is logged, never thrown — a
 * failed SMS must never break a sale or a ledger update.
 *
 * <p>When {@link SmsProperties#isUsable()} is false the service is a
 * logged no-op, so the app runs identically without SMS credentials.</p>
 */
@Component
public class EskizSmsService {

    private static final Logger log = LoggerFactory.getLogger(EskizSmsService.class);

    private final SmsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile String token;

    public EskizSmsService(SmsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isUsable() {
        return properties.isUsable();
    }

    /**
     * Sends one SMS. Returns true when the gateway accepts it. Phone may be
     * in any format; it is reduced to digits and forced to 998XXXXXXXXX.
     */
    public boolean send(String phone, String text) {
        if (!properties.isUsable()) {
            log.info("SMS disabled — would send to {}: {}", phone, oneLine(text));
            return false;
        }
        String to = toEskizPhone(phone);
        if (to == null) {
            log.warn("SMS skipped — invalid phone '{}'", phone);
            return false;
        }
        boolean ok = trySend(to, text, false);
        return ok || trySend(to, text, true);
    }

    /** One send attempt; {@code reauth} forces a fresh login first. */
    private boolean trySend(String to, String text, boolean reauth) {
        try {
            String bearer = reauth ? login() : currentToken();
            if (bearer == null) {
                return false;
            }
            String body = form(Map.of(
                    "mobile_phone", to,
                    "message", text,
                    "from", properties.from()));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.baseUrl() + "/message/sms/send"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + bearer)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                token = null;
                if (!reauth) {
                    return false; // caller retries once with reauth=true
                }
            }
            if (resp.statusCode() / 100 == 2) {
                log.info("SMS sent to {}", to);
                return true;
            }
            log.warn("SMS to {} failed: HTTP {} - {}", to, resp.statusCode(), resp.body());
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            log.warn("SMS to {} error: {}", to, ex.toString());
            return false;
        }
    }

    private String currentToken() {
        String t = token;
        return t != null ? t : login();
    }

    /** Authenticates and caches a fresh token; returns null on failure. */
    private synchronized String login() {
        try {
            String body = form(Map.of(
                    "email", properties.email(),
                    "password", properties.password()));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.baseUrl() + "/auth/login"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Eskiz login failed: HTTP {} - {}", resp.statusCode(), resp.body());
                return null;
            }
            JsonNode node = objectMapper.readTree(resp.body());
            String fresh = node.path("data").path("token").asText(null);
            if (fresh == null || fresh.isBlank()) {
                log.warn("Eskiz login returned no token: {}", resp.body());
                return null;
            }
            token = fresh;
            return fresh;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            log.warn("Eskiz login error: {}", ex.toString());
            return null;
        }
    }

    /** Reduces any phone to Eskiz's 998XXXXXXXXX, or null if not 9 local digits. */
    static String toEskizPhone(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("998")) {
            digits = digits.substring(3);
        }
        if (digits.length() != 9) {
            return null;
        }
        return "998" + digits;
    }

    private static String form(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }
}
