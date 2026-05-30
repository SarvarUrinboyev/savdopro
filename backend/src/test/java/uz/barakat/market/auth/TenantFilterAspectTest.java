package uz.barakat.market.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Fail-closed behaviour of {@link TenantFilterAspect}.
 *
 * <p>When a tenant scope is set but the row-level Hibernate filter cannot be
 * activated, the aspect must REFUSE the call (rethrow) rather than silently
 * run the query unscoped — otherwise every tenant's rows would leak. Closes
 * gap (b).
 */
class TenantFilterAspectTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void failsClosedWhenFilterCannotBeActivated() throws Throwable {
        EntityManager em = mock(EntityManager.class);
        // Simulate "no active Hibernate session" — unwrap blows up.
        when(em.unwrap(Session.class)).thenThrow(new PersistenceException("no session"));
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        TenantFilterAspect aspect = new TenantFilterAspect(em);

        TenantContext.setShopId(7L); // a scope IS requested

        assertThatThrownBy(() -> aspect.enableFilter(pjp))
                .isInstanceOf(PersistenceException.class);
        // The decisive assertion: the wrapped query NEVER ran unscoped.
        verify(pjp, never()).proceed();
    }

    @Test
    void enablesFilterAndProceedsWhenScopeIsSet() throws Throwable {
        EntityManager em = mock(EntityManager.class);
        Session session = mock(Session.class);
        Filter filter = mock(Filter.class);
        when(em.unwrap(Session.class)).thenReturn(session);
        when(session.enableFilter("tenantFilter")).thenReturn(filter);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");
        TenantFilterAspect aspect = new TenantFilterAspect(em);

        TenantContext.setShopId(7L);

        Object result = aspect.enableFilter(pjp);

        assertThat(result).isEqualTo("ok");
        verify(session).enableFilter("tenantFilter");
        verify(filter).setParameter("shopId", 7L);
        verify(pjp).proceed();
    }

    @Test
    void proceedsUntouchedWhenNoTenantScopeIsSet() throws Throwable {
        EntityManager em = mock(EntityManager.class);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");
        TenantFilterAspect aspect = new TenantFilterAspect(em);

        TenantContext.clear(); // no scope

        Object result = aspect.enableFilter(pjp);

        assertThat(result).isEqualTo("ok");
        verify(pjp).proceed();
        // No scope -> the aspect must not even touch the EntityManager.
        verifyNoInteractions(em);
    }
}
