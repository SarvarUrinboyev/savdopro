package uz.barakat.market.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Grants a Prometheus scraper access to {@code /actuator/prometheus} with a
 * static bearer token, so metrics can be collected without a short-lived JWT.
 *
 * <p>The token comes from {@code METRICS_SCRAPE_TOKEN}. When it is unset
 * (the default) this filter does nothing and the endpoint stays reachable
 * only by JWT-authenticated callers — existing behaviour is unchanged.
 *
 * <p>Scope is deliberately minimal: the synthetic principal is created only
 * for the exact {@code /actuator/prometheus} path, so the scrape token can
 * never be replayed against {@code /api/**} endpoints (which the
 * {@code authenticated()} catch-all would otherwise admit).
 *
 * <p>Ordering: must run AFTER {@link JwtAuthFilter}. That filter tries to
 * parse every {@code Bearer} header as a JWT and clears the security context
 * when parsing fails — which it always does for this opaque token — so
 * setting the authentication before it would be wiped out.
 */
@Component
public class MetricsScrapeTokenFilter extends OncePerRequestFilter {

    static final String PROMETHEUS_PATH = "/actuator/prometheus";

    private final String scrapeToken;

    public MetricsScrapeTokenFilter(
            @Value("${savdopro.metrics.scrape-token:}") String scrapeToken) {
        this.scrapeToken = scrapeToken == null ? "" : scrapeToken.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return scrapeToken.isEmpty() || !PROMETHEUS_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        var context = SecurityContextHolder.getContext();
        if (context.getAuthentication() == null) {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                byte[] presented = header.substring(7).trim()
                        .getBytes(StandardCharsets.UTF_8);
                byte[] expected = scrapeToken.getBytes(StandardCharsets.UTF_8);
                // Constant-time compare — the token is a long-lived secret.
                if (MessageDigest.isEqual(presented, expected)) {
                    context.setAuthentication(new UsernamePasswordAuthenticationToken(
                            "metrics-scraper", null,
                            List.of(new SimpleGrantedAuthority("ROLE_METRICS_SCRAPE"))));
                }
            }
        }
        chain.doFilter(request, response);
    }
}
