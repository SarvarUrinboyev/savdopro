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
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uz.barakat.license.exception.BadRequestException;

/**
 * X (Twitter) OAuth 2.0 — Authorization Code + PKCE. Unlike Google/Facebook
 * (browser popup → token), X uses a full-page redirect: the SPA sends the user
 * to X, X redirects back with a {@code code}, and the backend exchanges that
 * code (+ the PKCE {@code code_verifier} the SPA generated) for an access token,
 * then reads {@code /2/users/me}. X does NOT return an email.
 *
 * <p>Inert until {@code savdopro.license.oauth.x.client-id/client-secret/redirect-uri}
 * are set. Requires a (paid) X developer app.
 */
@Component
public class XOAuthVerifier {

    private static final Logger log = LoggerFactory.getLogger(XOAuthVerifier.class);

    private static final String TOKEN_URL = "https://api.twitter.com/2/oauth2/token";
    private static final String ME_URL = "https://api.twitter.com/2/users/me";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public XOAuthVerifier(
            @Value("${savdopro.license.oauth.x.client-id:}") String clientId,
            @Value("${savdopro.license.oauth.x.client-secret:}") String clientSecret,
            @Value("${savdopro.license.oauth.x.redirect-uri:}") String redirectUri) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.redirectUri = redirectUri == null ? "" : redirectUri.trim();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }

    public String clientIdOrNull() {
        return clientId.isBlank() ? null : clientId;
    }

    public String redirectUriOrNull() {
        return redirectUri.isBlank() ? null : redirectUri;
    }

    /**
     * Exchange the authorization {@code code} (+ PKCE verifier) for an access
     * token and return the verified X identity.
     */
    public XUser exchange(String code, String codeVerifier) {
        if (!isConfigured()) {
            throw new BadRequestException("X orqali kirish hozir o'chirilgan");
        }
        if (code == null || code.isBlank() || codeVerifier == null || codeVerifier.isBlank()) {
            throw new BadRequestException("X kodi to'liq emas");
        }
        try {
            String form = "code=" + enc(code)
                    + "&grant_type=authorization_code"
                    + "&redirect_uri=" + enc(redirectUri)
                    + "&code_verifier=" + enc(codeVerifier)
                    + "&client_id=" + enc(clientId);
            String basic = Base64.getEncoder().encodeToString(
                    (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest tokenReq = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", "Basic " + basic)
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> tokenResp = http.send(tokenReq, HttpResponse.BodyHandlers.ofString());
            if (tokenResp.statusCode() != 200) {
                log.warn("X token exchange failed: HTTP {} {}", tokenResp.statusCode(), tokenResp.body());
                throw new BadRequestException("X token almashinuvi muvaffaqiyatsiz");
            }
            String accessToken = text(mapper.readTree(tokenResp.body()), "access_token");
            if (accessToken == null || accessToken.isBlank()) {
                throw new BadRequestException("X tokeni olinmadi");
            }
            HttpRequest meReq = HttpRequest.newBuilder(URI.create(ME_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET().build();
            HttpResponse<String> meResp = http.send(meReq, HttpResponse.BodyHandlers.ofString());
            if (meResp.statusCode() != 200) {
                throw new BadRequestException("X profili o'qilmadi: HTTP " + meResp.statusCode());
            }
            JsonNode data = mapper.readTree(meResp.body()).path("data");
            String id = text(data, "id");
            if (id == null || id.isBlank()) {
                throw new BadRequestException("X hisobi aniqlanmadi");
            }
            return new XUser(id, text(data, "name"), text(data, "username"));
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("X verification failed: {}", ex.toString());
            throw new BadRequestException("X orqali kirib bo'lmadi");
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** Verified X identity. No email — X doesn't provide one. */
    public record XUser(String id, String name, String username) {
    }
}
