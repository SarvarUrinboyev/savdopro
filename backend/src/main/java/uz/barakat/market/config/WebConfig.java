package uz.barakat.market.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
 * production sets the property to include the public host.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(
            @Value("${app.web.allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://localhost:8086}")
            String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
