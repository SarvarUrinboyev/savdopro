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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uz.barakat.license.exception.BadRequestException;

/**
 * Verifies a Google OAuth access token obtained by the browser's Google
 * Identity Services account-chooser ("Sign in with Google", token model).
 *
 * <p>Two checks, both against Google's public endpoints — no client secret
 * needed:
 * <ol>
 *   <li><b>Audience</b> — {@code tokeninfo} returns the {@code aud} the token
 *       was minted for. We reject anything not issued for OUR client id, which
 *       is what stops an attacker pasting a token from a different Google app
 *       (token-substitution).</li>
 *   <li><b>Profile</b> — the OIDC {@code userinfo} endpoint returns the verified
 *       {@code sub}, {@code email}, {@code email_verified} and {@code name}.</li>
 * </ol>
 *
 * <p>Inert until {@code savdopro.license.oauth.google.client-id} is set: the
 * frontend never calls the endpoint (it only shows the Google button when the
 * client id is surfaced via {@code /signup/config}), and a direct hit returns a
 * clean 400. So shipping this never touches the normal login/register path.
 *
 * <p>Mirrors {@link EskizSmsProvider}'s transport: the JDK HTTP client + Jackson,
 * no extra dependency.
 */
@Component
public class GoogleOAuthVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthVerifier.class);

    private static final String TOKENINFO =
            "https://oauth2.googleapis.com/tokeninfo?access_token=";
    private static final String USERINFO =
            "https://openidconnect.googleapis.com/v1/userinfo";

    private final String clientId;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public GoogleOAuthVerifier(
            @Value("${savdopro.license.oauth.google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** True once the operator has configured the Google OAuth client id. */
    public boolean isConfigured() {
        return !clientId.isBlank();
    }

    /** The configured client id (or null) — surfaced to the signup screen. */
    public String clientIdOrNull() {
        return clientId.isBlank() ? null : clientId;
    }

    /**
     * Validate the browser-supplied access token and return the verified
     * Google identity. Throws {@link BadRequestException} on any failure so
     * the caller surfaces a friendly message; never returns partial data.
     */
    public GoogleUser verify(String accessToken) {
        if (!isConfigured()) {
            log.warn("Google login attempted but client-id is not configured");
            throw new BadRequestException("Google orqali kirish hozir o'chirilgan");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new BadRequestException("Google token bo'sh");
        }
        try {
            // 1) Security gate: the token MUST have been minted for our client id.
            JsonNode info = getJson(
                    TOKENINFO + URLEncoder.encode(accessToken, StandardCharsets.UTF_8), null);
            String aud = text(info, "aud");
            if (aud == null || !aud.equals(clientId)) {
                log.warn("Google token aud mismatch (got={})", aud);
                throw new BadRequestException("Google token bu ilova uchun emas");
            }
            // 2) Profile from the OIDC userinfo endpoint (uses the same token).
            JsonNode me = getJson(USERINFO, accessToken);
            String sub = text(me, "sub");
            String email = text(me, "email");
            boolean emailVerified = me.path("email_verified").asBoolean(false)
                    || "true".equalsIgnoreCase(text(me, "email_verified"));
            String name = text(me, "name");
            if (email == null || email.isBlank()) {
                throw new BadRequestException("Google hisobida email topilmadi");
            }
            if (!emailVerified) {
                throw new BadRequestException("Google email tasdiqlanmagan");
            }
            return new GoogleUser(sub, email.trim().toLowerCase(), name);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Google verification failed: {}", ex.toString());
            throw new BadRequestException("Google orqali kirib bo'lmadi");
        }
    }

    private JsonNode getJson(String url, String bearer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET();
        if (bearer != null) {
            b.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new BadRequestException("Google javobi: HTTP " + resp.statusCode());
        }
        return mapper.readTree(resp.body());
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** Verified Google identity. {@code name} may be null. */
    public record GoogleUser(String sub, String email, String name) {
    }
}
