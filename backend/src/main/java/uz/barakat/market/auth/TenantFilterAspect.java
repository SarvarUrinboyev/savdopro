package uz.barakat.market.auth;

import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Activates the Hibernate "tenantFilter" on the current EntityManager
 * session for every Spring-managed service call. Combined with the
 * {@code @Filter} declaration on {@code TenantScopedEntity}, this means
 * every read against a tenant-scoped entity automatically appends
 * {@code WHERE shop_id = :shopId}.
 *
 * <p>The aspect runs <em>inside</em> the @Transactional method (after
 * Spring opens the EntityManager) so {@code em.unwrap(Session.class)}
 * always yields an active session.
 */
@Aspect
@Component
// LOWEST = innermost: this aspect runs INSIDE the @Transactional proxy,
// so the EntityManager already has an active Hibernate session when we
// enable the filter.
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantFilterAspect {

    private final EntityManager entityManager;

    public TenantFilterAspect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(TenantFilterAspect.class);

    @Around("within(uz.barakat.market.service..*) || within(uz.barakat.market.auth..*Service)")
    public Object enableFilter(ProceedingJoinPoint pjp) throws Throwable {
        // Single-shop mode: WHERE shop_id = :shopId
        Long shopId = TenantContext.currentShopId();
        // Consolidated mode (main-shop "Hamma do'konlar"): WHERE shop_id IN (:shopIds)
        java.util.List<Long> shopIds = TenantContext.currentShopIds();
        if (shopId != null || (shopIds != null && !shopIds.isEmpty())) {
            try {
                Session session = entityManager.unwrap(Session.class);
                if (shopIds != null && !shopIds.isEmpty()) {
                    session.enableFilter("accountFilter")
                            .setParameterList("shopIds", shopIds);
                } else {
                    session.enableFilter("tenantFilter")
                            .setParameter("shopId", shopId);
                }
            } catch (RuntimeException ex) {
                // Fail CLOSED: a tenant scope was requested but the row-level
                // filter could not be activated. Running the query unscoped
                // would expose every tenant's rows, so refuse outright rather
                // than silently widen the result set.
                log.error("Refusing to run unscoped — tenant filter activation failed", ex);
                throw ex;
            }
        }
        return pjp.proceed();
    }
}
