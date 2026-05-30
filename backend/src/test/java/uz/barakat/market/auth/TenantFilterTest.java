package uz.barakat.market.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import uz.barakat.market.repository.ShopRepository;

/**
 * Tenant-isolation guarantees enforced by {@link TenantFilter}. These are
 * the security crux of the multi-tenant web build: a caller must never be
 * able to scope queries to a shop it does not own.
 */
class TenantFilterTest {

    private final ShopRepository shops = mock(ShopRepository.class);
    private final TenantFilter filter = new TenantFilter(shops);

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void ownedShopHeaderSetsTenantScope() throws Exception {
        when(shops.existsByIdAndAccountId(7L, 42L)).thenReturn(true);
        MockHttpServletRequest req = authed(42L, "ACCOUNT_OWNER");
        req.addHeader("X-Shop-Id", "7");
        MockHttpServletResponse res = new MockHttpServletResponse();
        long[] seenScope = {-1L};
        FilterChain chain = (rq, rs) -> seenScope[0] = TenantContext.requireShopId();

        filter.doFilter(req, res, chain);

        assertThat(seenScope[0]).isEqualTo(7L);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void foreignShopHeaderIsRejectedWith403() throws Exception {
        // Shop 7 is NOT owned by account 42 (different tenant, or absent).
        when(shops.existsByIdAndAccountId(7L, 42L)).thenReturn(false);
        MockHttpServletRequest req = authed(42L, "ACCOUNT_OWNER");
        req.addHeader("X-Shop-Id", "7");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void shopHeaderFromUnauthenticatedCallerIsIgnored() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(); // no accountId attribute
        req.setRequestURI("/api/products");
        req.addHeader("X-Shop-Id", "7");
        MockHttpServletResponse res = new MockHttpServletResponse();
        boolean[] chained = {false};
        FilterChain chain = (rq, rs) -> chained[0] = true;

        filter.doFilter(req, res, chain);

        // The anonymous request is let through (Spring Security rejects it next),
        // but the spoofed shop header is never trusted / looked up.
        assertThat(chained[0]).isTrue();
        assertThat(res.getStatus()).isEqualTo(200);
        verify(shops, never()).existsByIdAndAccountId(anyLong(), any());
    }

    @Test
    void consolidatedAllModeDeniedForShopUser() throws Exception {
        MockHttpServletRequest req = authed(42L, "SHOP_USER");
        req.addHeader("X-Shop-Id", "ALL");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    private static MockHttpServletRequest authed(Long accountId, String role) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/products");
        req.setAttribute(JwtAuthFilter.ATTR_ACCOUNT_ID, accountId);
        req.setAttribute(JwtAuthFilter.ATTR_ROLE, role);
        return req;
    }
}
