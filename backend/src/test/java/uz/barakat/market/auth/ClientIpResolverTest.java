package uz.barakat.market.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * The forwarded header must only be believed when the request arrives from a
 * configured trusted proxy — otherwise a client could spoof its IP to dodge
 * {@link RateLimitFilter}.
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
        assertThat(resolver.resolve(req("203.0.113.9", "1.2.3.4"))).isEqualTo("203.0.113.9");
    }

    @Test
    void spoofedForwardedFromUntrustedPeerIsIgnored() {
        ClientIpResolver resolver = new ClientIpResolver(true, "127.0.0.1/32");
        assertThat(resolver.resolve(req("203.0.113.9", "1.2.3.4"))).isEqualTo("203.0.113.9");
    }

    @Test
    void forwardedFromTrustedProxyIsAccepted() {
        ClientIpResolver resolver = new ClientIpResolver(true, "127.0.0.1/32,::1/128");
        assertThat(resolver.resolve(req("127.0.0.1", "198.51.100.7"))).isEqualTo("198.51.100.7");
    }

    @Test
    void takesLeftmostClientFromForwardedChain() {
        ClientIpResolver resolver = new ClientIpResolver(true, "127.0.0.1/32");
        assertThat(resolver.resolve(req("127.0.0.1", "198.51.100.7, 10.0.0.1, 127.0.0.1")))
                .isEqualTo("198.51.100.7");
    }

    @Test
    void malformedForwardedFallsBackToPeerSafely() {
        ClientIpResolver resolver = new ClientIpResolver(true, "127.0.0.1/32");
        assertThat(resolver.resolve(req("127.0.0.1", "not-an-ip;drop table")))
                .isEqualTo("127.0.0.1");
    }

    @Test
    void trustEnabledButNoCidrsDoesNotTrustHeader() {
        ClientIpResolver resolver = new ClientIpResolver(true, "");
        assertThat(resolver.resolve(req("127.0.0.1", "9.9.9.9"))).isEqualTo("127.0.0.1");
    }

    @Test
    void cidrRangeMatchAndMiss() {
        ClientIpResolver resolver = new ClientIpResolver(true, "10.0.0.0/8");
        assertThat(resolver.resolve(req("10.4.5.6", "198.51.100.7"))).isEqualTo("198.51.100.7");
        assertThat(resolver.resolve(req("203.0.113.1", "198.51.100.7"))).isEqualTo("203.0.113.1");
    }
}
