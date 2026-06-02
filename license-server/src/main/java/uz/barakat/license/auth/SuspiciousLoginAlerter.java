package uz.barakat.license.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fire-and-forget Telegram notifier for security events on the
 * License Server. Today it only emits one event — a brute-force
 * lockout trip from {@link LoginRateLimiter} — but the contract is
 * deliberately narrow so we can grow it (e.g. super-admin password
 * reset, account-block) without dragging in the full backend
 * {@code TelegramService}.
 *
 * <p>Configured via two env vars (or {@code application-local.properties}):
 * <ul>
 *   <li>{@code SAVDOPRO_LICENSE_ALERT_BOT_TOKEN}</li>
 *   <li>{@code SAVDOPRO_LICENSE_ALERT_CHAT_ID}</li>
 * </ul>
 * If either is blank the alerter logs the event and returns. Production
 * deploys are expected to set both; local dev never needs to.
 */
@Component
public class SuspiciousLoginAlerter {

    private static final Logger log = LoggerFactory.getLogger(SuspiciousLoginAlerter.class);
    private static final String API = "https://api.telegram.org/bot";

    private final String botToken;
    private final String chatId;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SuspiciousLoginAlerter(
            @Value("${savdopro.license.alert.telegram.bot-token:}") String botToken,
            @Value("${savdopro.license.alert.telegram.chat-id:}") String chatId,
            ObjectMapper objectMapper) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** True if both bot token and chat id are configured. */
    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank()
                && chatId != null && !chatId.isBlank();
    }

    /**
     * Fire-and-forget: returns immediately, the HTTP call runs on a
     * background thread. Errors are logged at WARN and never propagated
     * to the caller (the rate-limit path must not depend on Telegram
     * being reachable).
     */
    public void notifyLockout(String ip, Instant lockedUntil) {
        if (!isConfigured()) {
            log.info("Login lockout alert skipped (Telegram not configured): ip={} until={}",
                    ip, lockedUntil);
            return;
        }
        String text = String.format(
                "🚨 SavdoPRO License Server: login lockout%n"
                        + "IP: %s%n"
                        + "Locked until: %s UTC",
                ip, lockedUntil);
        CompletableFuture.runAsync(() -> sendMessage(text));
    }

    /**
     * Fire-and-forget: tells the super-admin a new merchant just signed up
     * for the free trial. Never blocks or fails the signup flow.
     */
    public void notifyNewSignup(String businessName, String phone,
                                String username, int trialDays) {
        if (!isConfigured()) {
            log.info("New-signup alert skipped (Telegram not configured): {}", businessName);
            return;
        }
        String text = String.format(
                "🆕 SavdoPRO — Yangi mijoz qo'shildi!%n"
                        + "🏪 Biznes: %s%n"
                        + "📞 Telefon: %s%n"
                        + "👤 Login: %s%n"
                        + "🎁 %d kunlik bepul sinov boshlandi.",
                businessName, (phone == null || phone.isBlank()) ? "—" : phone,
                username, trialDays);
        CompletableFuture.runAsync(() -> sendMessage(text));
    }

    /** Visible for testing — direct send, no background thread. */
    void sendMessage(String text) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "chat_id", chatId,
                    "text", text,
                    "disable_web_page_preview", true));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API + botToken + "/sendMessage"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("Telegram alert HTTP {} — body: {}",
                        response.statusCode(), response.body());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("Telegram alert failed: {}", ex.toString());
        }
    }
}
