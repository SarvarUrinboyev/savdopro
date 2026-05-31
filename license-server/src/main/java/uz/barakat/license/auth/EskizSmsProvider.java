package uz.barakat.license.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real SMS transport via <a href="https://eskiz.uz">Eskiz.uz</a> — the
 * dominant Uzbek SMS gateway. Activated only when {@code sms.provider=eskiz}
 * (see {@link LoggingSmsProviderConfig}); otherwise the logging stub stays in
 * place, so dev / CI never send real messages.
 *
 * <p>Flow: log in once with the merchant email + password to get a bearer
 * token (Eskiz tokens last ~30 days), cache it, and POST each message to
 * {@code /api/message/sms/send}. On a 401 we re-login once and retry, so an
 * expired token self-heals. Uses the JDK HTTP client — no extra dependency.
 *
 * <p>Credentials come from the operator's Eskiz cabinet; the API itself is
 * public, so this is complete and only needs the keys in config to go live.
 */
class EskizSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(EskizSmsProvider.class);

    private final String email;
    private final String password;
    private final String from;
    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String token;

    EskizSmsProvider(String email, String password, String from, String baseUrl) {
        this.email = email;
        this.password = password;
        this.from = from;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public boolean send(String phone, String body) {
        String mobile = normalizePhone(phone);
        if (mobile.isEmpty()) {
            log.warn("Eskiz: empty/invalid phone, skipping send");
            return false;
        }
        try {
            if (token == null) {
                login();
            }
            int status = postSms(mobile, body);
            if (status == 401) {
                // Token expired / revoked — re-authenticate once and retry.
                login();
                status = postSms(mobile, body);
            }
            boolean ok = status >= 200 && status < 300;
            if (!ok) {
                log.warn("Eskiz send failed: HTTP {}", status);
            }
            return ok;
        } catch (Exception e) {
            log.warn("Eskiz send error: {}", e.toString());
            return false;
        }
    }

    private synchronized void login() throws Exception {
        String form = "email=" + enc(email) + "&password=" + enc(password);
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/login"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("Eskiz login HTTP " + resp.statusCode());
        }
        String t = parseToken(resp.body());
        if (t == null || t.isBlank()) {
            throw new IllegalStateException("Eskiz login: no token in response");
        }
        this.token = t;
        log.info("Eskiz: authenticated (token cached)");
    }

    private int postSms(String mobile, String message) throws Exception {
        String form = "mobile_phone=" + enc(mobile)
                + "&message=" + enc(message)
                + "&from=" + enc(from);
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/message/sms/send"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode();
    }

    /** Extract {@code data.token} from Eskiz's login response. Package-visible for tests. */
    String parseToken(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode tok = root.path("data").path("token");
            return tok.isMissingNode() ? null : tok.asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Eskiz wants a bare MSISDN (e.g. {@code 998901234567}): digits only,
     * leading {@code +} and separators stripped. A local {@code 9XXXXXXXX}
     * (9 digits) is prefixed with the Uzbek country code. Package-visible for tests.
     */
    static String normalizePhone(String raw) {
        if (raw == null) {
            return "";
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 9) {
            digits = "998" + digits;
        }
        return digits;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String s) {
        return (s != null && s.endsWith("/")) ? s.substring(0, s.length() - 1) : s;
    }
}
