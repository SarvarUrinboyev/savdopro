package uz.barakat.market.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import uz.barakat.market.service.AuditService;

/**
 * Records every successful-or-failed mutating /api request into the local
 * audit trail. Runs in {@code afterCompletion}, where the request's tenant +
 * user context are still on the thread (the {@code TenantFilter} clears them
 * only after the dispatch returns). Strictly best-effort — a failure to audit
 * never affects the response.
 */
@Component
public class AuditInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);

    private final AuditService audit;

    public AuditInterceptor(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res,
                                Object handler, Exception ex) {
        String method = req.getMethod();
        if (!isMutating(method)) {
            return;
        }
        String path = req.getRequestURI();
        if (path == null || !path.startsWith("/api/") || path.startsWith("/api/audit")) {
            return;
        }
        Long shopId = TenantContext.currentShopId();
        if (shopId == null) {
            return;   // no single-shop scope (admin / consolidated) — not a shop write
        }
        Object actor = req.getAttribute(JwtAuthFilter.ATTR_USERNAME);
        try {
            audit.record(shopId, actor instanceof String s ? s : null,
                    method, path, res.getStatus(), clientIp(req));
        } catch (RuntimeException e) {
            log.debug("Audit record skipped: {}", e.toString());
        }
    }

    private static boolean isMutating(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }
}
