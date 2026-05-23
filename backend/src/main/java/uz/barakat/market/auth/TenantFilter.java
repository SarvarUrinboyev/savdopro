package uz.barakat.market.auth;

import jakarta.persistence.EntityManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.hibernate.Session;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Extracts {@code X-Shop-Id} from the incoming request, parks it on
 * the thread via {@link TenantContext} and enables the Hibernate
 * "tenantFilter" so every read on a {@code @Filter}-annotated entity
 * automatically gets a {@code WHERE shop_id = :shopId} clause.
 *
 * <p>Runs <strong>after</strong> {@link JwtAuthFilter} so the user is
 * already authenticated by the time we open a Hibernate session.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class TenantFilter extends OncePerRequestFilter {

    private final EntityManager entityManager;

    public TenantFilter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        Long shopId = parseShopId(request.getHeader("X-Shop-Id"));
        TenantContext.setShopId(shopId);
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private static Long parseShopId(String header) {
        if (header == null || header.isBlank()) return null;
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
