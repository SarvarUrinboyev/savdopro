package uz.barakat.market.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import uz.barakat.market.domain.ApiKey;

/** Unit tests for API-key authentication and its non-interference with JWTs. */
@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @Mock
    private ApiKeyService apiKeys;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private ApiKeyAuthFilter filter() {
        return new ApiKeyAuthFilter(apiKeys);
    }

    private static ApiKey key(boolean active, LocalDateTime expiresAt) {
        ApiKey k = new ApiKey();
        k.setId(7L);
        k.setShopId(3L);
        k.setActive(active);
        k.setExpiresAt(expiresAt);
        k.setScopes("catalog:read,sales:read");
        return k;
    }

    @Test
    void validKeySetsAuthorityScopesAndTenantAttributes() throws Exception {
        ApiKey k = key(true, null);
        when(apiKeys.resolve("sk_live_ABC")).thenReturn(Optional.of(k));
        when(apiKeys.scopesOf(k)).thenReturn(List.of("catalog:read", "sales:read"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer sk_live_ABC");
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req, new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "valid key authenticates");
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_catalog:read")));
        assertEquals(7L, req.getAttribute(ApiKeyAuthFilter.ATTR_API_KEY_ID));
        assertEquals(3L, req.getAttribute(ApiKeyAuthFilter.ATTR_API_KEY_SHOP_ID));
        assertNotNull(chain.getRequest(), "chain continues");
    }

    @Test
    void jwtBearerTokenIsLeftUntouched() throws Exception {
        // A normal JWT bearer must pass through without the API-key filter acting.
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig");
        filter().doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void xApiKeyHeaderIsAccepted() throws Exception {
        ApiKey k = key(true, null);
        lenient().when(apiKeys.resolve("sk_live_XYZ")).thenReturn(Optional.of(k));
        lenient().when(apiKeys.scopesOf(k)).thenReturn(List.of("catalog:read"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Api-Key", "sk_live_XYZ");
        filter().doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void unknownKeySetsNoAuthentication() throws Exception {
        when(apiKeys.resolve(any())).thenReturn(Optional.empty());
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer sk_live_NOPE");
        filter().doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void expiredKeySetsNoAuthentication() throws Exception {
        ApiKey expired = key(true, LocalDateTime.now().minusDays(1));
        when(apiKeys.resolve("sk_live_OLD")).thenReturn(Optional.of(expired));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer sk_live_OLD");
        filter().doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void disabledKeySetsNoAuthentication() throws Exception {
        ApiKey revoked = key(false, null);
        when(apiKeys.resolve("sk_live_REV")).thenReturn(Optional.of(revoked));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer sk_live_REV");
        filter().doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
