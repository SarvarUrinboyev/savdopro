package uz.barakat.market.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The rate-limit filter lets a caller through up to the per-minute cap, then
 * answers 429 without invoking the chain; an exempt/disabled path always passes.
 */
class RateLimitFilterTest {

    private static MockHttpServletRequest apiRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/products");
        req.setAttribute(JwtAuthFilter.ATTR_USERNAME, "cashier1");
        return req;
    }

    @Test
    void blocksAfterTheLimitIsReached() throws Exception {
        // Floor is 30/min; passing a smaller value clamps up to 30.
        RateLimitFilter filter = new RateLimitFilter(true, 30, new SimpleMeterRegistry(), new ClientIpResolver(false, ""));
        int[] passed = {0};
        FilterChain chain = (req, res) -> passed[0]++;

        for (int i = 0; i < 30; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(apiRequest(), res, chain);
            assertThat(res.getStatus()).isNotEqualTo(429);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(apiRequest(), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(passed[0]).isEqualTo(30);   // the blocked request never hit the chain
    }

    @Test
    void disabledFilterAlwaysPasses() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(false, 30, new SimpleMeterRegistry(), new ClientIpResolver(false, ""));
        int[] passed = {0};
        FilterChain chain = (req, res) -> passed[0]++;
        for (int i = 0; i < 100; i++) {
            filter.doFilter(apiRequest(), new MockHttpServletResponse(), chain);
        }
        assertThat(passed[0]).isEqualTo(100);
    }

    @Test
    void healthIsNeverThrottled() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 30, new SimpleMeterRegistry(), new ClientIpResolver(false, ""));
        int[] passed = {0};
        FilterChain chain = (req, res) -> passed[0]++;
        for (int i = 0; i < 50; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/health");
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }
        assertThat(passed[0]).isEqualTo(50);
    }

    /**
     * Audit hardening: an unauthenticated caller must not be able to mint a
     * fresh per-IP bucket every request by rotating a spoofed X-Forwarded-For.
     * With proxy-header trust off (the default), every request from the same
     * socket address shares one bucket regardless of the forged header.
     */
    @Test
    void spoofedForwardedForCannotBypassTheIpBucket() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 30, new SimpleMeterRegistry(),
                new ClientIpResolver(false, ""));
        int[] passed = {0};
        FilterChain chain = (req, res) -> passed[0]++;

        for (int i = 0; i < 30; i++) {
            filter.doFilter(spoofedRequest(i), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(spoofedRequest(99), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(429);   // a different XFF did NOT help
        assertThat(passed[0]).isEqualTo(30);
    }

    /** Unauthenticated request from one socket address but a rotating fake XFF. */
    private static MockHttpServletRequest spoofedRequest(int i) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/products");
        req.setRemoteAddr("10.0.0.5");
        req.addHeader("X-Forwarded-For", "203.0.113." + i);
        return req;
    }
}
