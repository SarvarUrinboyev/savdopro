package uz.barakat.market.config;

import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uz.barakat.market.auth.AuditInterceptor;

/**
 * CORS for the API.
 *
 * <p>The hosted web portal runs on its OWN origin (the nip.io host, a custom
 * domain, etc.). Browsers attach an {@code Origin} header to every mutating
 * request (POST/PUT/PATCH/DELETE) — even same-origin ones — so Spring treats
 * them as CORS requests and enforces the allow-list. If the portal origin is
 * missing here, every write fails with "Invalid CORS request" (reads still
 * work because same-origin GETs carry no Origin header).
 *
 * <p>Origins are configurable via {@code app.web.allowed-origins}
 * (comma-separated). The localhost dev origins are the built-in default;
 * production sets the property to the public host.
 *
 * <h2>Fail closed</h2>
 * Because the API sends credentials ({@code allowCredentials(true)}), the
 * allow-list must never be the bare wildcard {@code *} — that would let ANY
 * website make authenticated cross-origin calls. This config refuses to start
 * if it sees {@code *}, and (in the {@code prod} profile) refuses to start with
 * an empty list, instead of silently degrading to wildcard.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);
    private static final String WILDCARD = "*";

    private final String[] allowedOrigins;
    private final AuditInterceptor auditInterceptor;

    public WebConfig(
            @Value("${app.web.allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://localhost:8086}")
            String[] allowedOrigins,
            AuditInterceptor auditInterceptor,
            Environment environment) {
        this.allowedOrigins = sanitize(allowedOrigins, environment);
        this.auditInterceptor = auditInterceptor;
    }

    /**
     * Drop blank entries (a stray comma in the env var must not widen the list)
     * and fail closed on a wildcard-with-credentials or an empty prod list.
     */
    private static String[] sanitize(String[] origins, Environment environment) {
        List<String> cleaned = Arrays.stream(origins == null ? new String[0] : origins)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (cleaned.contains(WILDCARD)) {
            throw new IllegalStateException(
                    "REFUSING TO START: app.web.allowed-origins contains \"*\" while CORS "
                    + "credentials are enabled — any website could then make authenticated "
                    + "cross-origin calls. Set WEB_ALLOWED_ORIGINS to the explicit merchant "
                    + "portal origin(s), comma-separated (e.g. https://shop.example.com).");
        }

        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        if (cleaned.isEmpty()) {
            if (prod) {
                throw new IllegalStateException(
                        "REFUSING TO START: no app.web.allowed-origins configured for the prod "
                        + "profile. Set WEB_ALLOWED_ORIGINS to the explicit merchant portal "
                        + "origin(s), comma-separated.");
            }
            log.warn("No CORS origins configured — cross-origin browser writes will be rejected.");
        }
        return cleaned.toArray(new String[0]);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.length == 0) {
            return;   // fail closed: no cross-origin access rather than wildcard
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
