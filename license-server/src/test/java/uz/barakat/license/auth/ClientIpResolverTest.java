package uz.barakat.license.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Hardening for the audit finding "X-Forwarded-For is trusted directly".
 * The forwarded header must only be believed when the request actually arrives
 * from a configured trusted proxy — otherwise a client could spoof its IP to
 * dodge the login rate limiter or poison the audit log.
 */
class ClientIpResolverTest {

    private static MockHttpServletRequest req(String remoteAddr, String xff) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remoteAddr);
        if (xff != null) {
            r.addHeader("X-Forwarded-For", xff);
        }
        return r;
    }

    @Test
    void ignoresForwardedHeaderWhenTrustDisabledByDefault() {
        ClientIpResolver resolver = new ClientIpResolver(false, "");
        assertEquals("203.0.113.9", resolver.resolve(req("203.0.113.9", "1.2.3.4")));
    }

    @Test
    void spoofedForwardedFromUntrustedPeerIsIgnored() {
        ClientIpResolver resolver = new ClientIpResolver(true, "127.0.0.1/32");
        // Straight from the internet (not via the proxy) → header is forged.
        assertEquals("203.0.113.9", resolver.resolve(req("203.0.113.9", "1.2.3.4")));
    }

    @Test
    void forwardedFromTrustedProxyIsAccepted() {
        ClientIpResolver resolver = new ClientIpResolver(true, "127.0.0.1/32,::1/128");
        assertEquals("198.51.100.7", resolver.resolve(req("127.0.0.1", "198.51.100.7")));
    }

    @Test
    void takesLeftmostClientFromForwardedChain() {
        ClientIpResolver resolver = new ClientIpResolver(true, "127.0.0.1/32");
        assertEquals("198.51.100.7",
                resolver.resolve(req("127.0.0.1", "198.51.100.7, 10.0.0.1, 127.0.0.1")));
    }

    @Test
    void malformedForwardedFallsBackToPeerSafely() {
        ClientIpResolver resolver = new ClientIpResolver(true, "127.0.0.1/32");
        assertEquals("127.0.0.1", resolver.resolve(req("127.0.0.1", "not-an-ip;drop table")));
    }

    @Test
    void hexLikeHostnameIsNotResolvedViaDns() {
        ClientIpResolver resolver = new ClientIpResolver(true, "127.0.0.1/32");
        // "cafe" is all hex chars but not an IP literal — must be rejected, not
        // sent to DNS, and the peer used instead.
        assertEquals("127.0.0.1", resolver.resolve(req("127.0.0.1", "cafe")));
    }

    @Test
    void trustEnabledButNoCidrsDoesNotTrustHeader() {
        ClientIpResolver resolver = new ClientIpResolver(true, "");
        assertEquals("127.0.0.1", resolver.resolve(req("127.0.0.1", "9.9.9.9")));
    }

    @Test
    void cidrRangeMatchAndMiss() {
        ClientIpResolver resolver = new ClientIpResolver(true, "10.0.0.0/8");
        // Inside 10.0.0.0/8 → trusted, forwarded IP used.
        assertEquals("198.51.100.7", resolver.resolve(req("10.4.5.6", "198.51.100.7")));
        // Outside the range → not trusted, peer used.
        assertEquals("203.0.113.1", resolver.resolve(req("203.0.113.1", "198.51.100.7")));
    }
}
