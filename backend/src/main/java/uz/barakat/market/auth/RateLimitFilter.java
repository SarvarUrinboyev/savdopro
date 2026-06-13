package uz.barakat.market.auth;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-caller request throttling for the local API. A fixed 1-minute window
 * keyed by the authenticated username (falling back to the client IP for the
 * few unauthenticated paths). Generous by default and fully switch-off-able via
 * {@code app.ratelimit.enabled=false} so it can never wedge a busy till.
 *
 * <p>Ordered LOWEST so it runs AFTER the Spring Security chain — the JWT user
 * attribute is already set, so each cashier gets their own bucket rather than
 * sharing one per-shop NAT IP. Health, payment webhooks, websockets and the
 * static SPA are never throttled.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_KEYS = 20_000;   // memory guard

    private final boolean enabled;
    private final int perMinute;
    private final MeterRegistry metrics;
    private final ConcurrentHashMap<String, Hits> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${app.ratelimit.enabled:true}") boolean enabled,
            @Value("${app.ratelimit.requests-per-minute:600}") int perMinute,
            MeterRegistry metrics) {
        this.enabled = enabled;
        this.perMinute = Math.max(30, perMinute);
        this.metrics = metrics;
        log.info("Rate limit: {} ({} req/min/caller)", enabled ? "ON" : "OFF", this.perMinute);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        if (!enabled) {
            return true;
        }
        String p = req.getRequestURI();
        // Only throttle the data API; never health probes or self-auth webhooks.
        return p == null || !p.startsWith("/api/")
                || p.startsWith("/api/health") || p.startsWith("/api/pay");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String key = callerKey(req);
        if (!allow(key)) {
            metrics.counter("security.ratelimit.blocked").increment();
            log.warn("Rate limit exceeded for {} on {} {}", key, req.getMethod(), req.getRequestURI());
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write(
                    "{\"message\":\"So'rovlar juda ko'p — bir oz kuting\",\"code\":\"RATE_LIMIT\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    private boolean allow(String key) {
        if (buckets.size() > MAX_KEYS) {
            sweep();
        }
        Hits h = buckets.computeIfAbsent(key, k -> new Hits());
        long now = System.currentTimeMillis();
        synchronized (h) {
            if (now - h.windowStart >= WINDOW_MS) {
                h.windowStart = now;
                h.count = 0;
            }
            h.count++;
            return h.count <= perMinute;
        }
    }

    /** Drop windows that have fully elapsed — keeps the map bounded. */
    private void sweep() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(e -> now - e.getValue().windowStart >= WINDOW_MS);
    }

    private String callerKey(HttpServletRequest req) {
        Object user = req.getAttribute(JwtAuthFilter.ATTR_USERNAME);
        if (user instanceof String s && !s.isBlank()) {
            return "u:" + s;
        }
        return "ip:" + clientIp(req);
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }

    private static final class Hits {
        long windowStart;
        int count;
    }
}
