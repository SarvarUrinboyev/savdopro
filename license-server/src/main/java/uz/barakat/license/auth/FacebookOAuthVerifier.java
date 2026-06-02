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
 * Verifies a Facebook Login access token (obtained by the browser's FB JS SDK).
 *
 * <ol>
 *   <li><b>Audience</b> — Graph {@code debug_token} (called with an app access
 *       token = {@code <app-id>|<app-secret>}) returns the app the token was
 *       issued for and whether it's valid. We reject anything not for OUR app.</li>
 *   <li><b>Profile</b> — Graph {@code /me?fields=id,name,email}. Email is only
 *       present if the app passed Facebook's App Review for the email permission;
 *       when absent the caller keys the account by {@code fb_<id>}.</li>
 * </ol>
 *
 * <p>Inert until {@code savdopro.license.oauth.facebook.app-id/app-secret} are
 * set — mirrors {@link GoogleOAuthVerifier}.
 */
@Component
public class FacebookOAuthVerifier {

    private static final Logger log = LoggerFactory.getLogger(FacebookOAuthVerifier.class);

    private static final String GRAPH = "https://graph.facebook.com/v19.0";

    private final String appId;
    private final String appSecret;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public FacebookOAuthVerifier(
            @Value("${savdopro.license.oauth.facebook.app-id:}") String appId,
            @Value("${savdopro.license.oauth.facebook.app-secret:}") String appSecret) {
        this.appId = appId == null ? "" : appId.trim();
        this.appSecret = appSecret == null ? "" : appSecret.trim();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public boolean isConfigured() {
        return !appId.isBlank() && !appSecret.isBlank();
    }

    /** The public app id — surfaced to the signup screen for the FB SDK init. */
    public String appIdOrNull() {
        return appId.isBlank() ? null : appId;
    }

    public FacebookUser verify(String accessToken) {
        if (!isConfigured()) {
            throw new BadRequestException("Facebook orqali kirish hozir o'chirilgan");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new BadRequestException("Facebook token bo'sh");
        }
        try {
            String appToken = URLEncoder.encode(appId + "|" + appSecret, StandardCharsets.UTF_8);
            String enc = URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
            JsonNode dbg = getJson(GRAPH + "/debug_token?input_token=" + enc
                    + "&access_token=" + appToken).path("data");
            if (!dbg.path("is_valid").asBoolean(false)) {
                throw new BadRequestException("Facebook token yaroqsiz");
            }
            if (!appId.equals(text(dbg, "app_id"))) {
                log.warn("Facebook token app_id mismatch (got={})", text(dbg, "app_id"));
                throw new BadRequestException("Facebook token bu ilova uchun emas");
            }
            JsonNode me = getJson(GRAPH + "/me?fields=id,name,email&access_token=" + enc);
            String id = text(me, "id");
            if (id == null || id.isBlank()) {
                throw new BadRequestException("Facebook hisobi aniqlanmadi");
            }
            return new FacebookUser(id, text(me, "email"), text(me, "name"));
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Facebook verification failed: {}", ex.toString());
            throw new BadRequestException("Facebook orqali kirib bo'lmadi");
        }
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10)).header("Accept", "application/json").GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new BadRequestException("Facebook javobi: HTTP " + resp.statusCode());
        }
        return mapper.readTree(resp.body());
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** Verified Facebook identity. {@code email} may be null (no App Review). */
    public record FacebookUser(String id, String email, String name) {
    }
}
