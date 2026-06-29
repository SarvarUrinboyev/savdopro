package uz.barakat.license.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the real client IP, trusting {@code X-Forwarded-For} /
 * {@code X-Real-IP} ONLY when the immediate peer ({@code getRemoteAddr()}) is a
 * configured trusted proxy.
 *
 * <p>Config (env names in brackets):
 * <ul>
 *   <li>{@code app.proxy.trust-headers} [{@code TRUST_PROXY_HEADERS}] — master
 *       switch, default {@code false}.</li>
 *   <li>{@code app.proxy.trusted-cidrs} [{@code TRUSTED_PROXY_CIDRS}] —
 *       comma-separated CIDRs whose requests may carry a forwarded IP
 *       (e.g. {@code 127.0.0.1/32,::1/128} for an nginx on the same host).</li>
 * </ul>
 *
 * <p>The default is fail-safe: with {@code trust-headers=false} the header is
 * ignored and the direct socket address is used, so an arbitrary client cannot
 * spoof its IP to dodge the login rate limiter or poison the audit log. Only a
 * request that actually arrives from a trusted proxy address has its forwarded
 * header believed.
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private final boolean trustHeaders;
    private final List<Cidr> trustedProxies;

    public ClientIpResolver(
            @Value("${app.proxy.trust-headers:false}") boolean trustHeaders,
            @Value("${app.proxy.trusted-cidrs:}") String trustedCidrs) {
        this.trustHeaders = trustHeaders;
        this.trustedProxies = parseCidrs(trustedCidrs);
        if (trustHeaders && trustedProxies.isEmpty()) {
            log.warn("app.proxy.trust-headers=true but app.proxy.trusted-cidrs is empty — "
                    + "X-Forwarded-For will NOT be trusted. Set the proxy CIDR (e.g. 127.0.0.1/32).");
        }
    }

    /** @return the client IP — the forwarded one only if the peer is trusted. */
    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!trustHeaders || !isTrustedProxy(remote)) {
            return remote;
        }
        String fromXff = firstValidIp(request.getHeader("X-Forwarded-For"));
        if (fromXff != null) {
            return fromXff;
        }
        String realIp = normalize(request.getHeader("X-Real-IP"));
        return realIp != null ? realIp : remote;
    }

    private boolean isTrustedProxy(String ip) {
        InetAddress addr = parse(ip);
        if (addr == null) {
            return false;
        }
        for (Cidr c : trustedProxies) {
            if (c.contains(addr)) {
                return true;
            }
        }
        return false;
    }

    /** First (leftmost = original client) syntactically valid address in the list. */
    private static String firstValidIp(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        for (String part : headerValue.split(",")) {
            String norm = normalize(part);
            if (norm != null) {
                return norm;
            }
        }
        return null;
    }

    /** @return the canonical host address if {@code raw} is a valid IP literal, else null. */
    private static String normalize(String raw) {
        InetAddress addr = parse(raw);
        return addr == null ? null : addr.getHostAddress();
    }

    private static InetAddress parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        // Strip an optional [ipv6]:port / [ipv6] wrapper and a zone id.
        if (s.startsWith("[")) {
            int close = s.indexOf(']');
            if (close > 0) {
                s = s.substring(1, close);
            }
        }
        int pct = s.indexOf('%');
        if (pct > 0) {
            s = s.substring(0, pct);
        }
        if (s.indexOf(':') >= 0) {
            // IPv6 literal: a colon-bearing string is never DNS-resolved by the
            // JDK, but guard the charset first so we don't pass it junk.
            if (!s.matches("[0-9A-Fa-f:.]+")) {
                return null;
            }
            try {
                return InetAddress.getByName(s);
            } catch (UnknownHostException e) {
                return null;
            }
        }
        // IPv4 dotted-quad, validated numerically and built from bytes so we
        // NEVER fall through to a DNS lookup on attacker-influenced input
        // (e.g. a pure-hex string like "cafe" would otherwise resolve as a host).
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) {
            if (!parts[i].matches("\\d{1,3}")) {
                return null;
            }
            int v = Integer.parseInt(parts[i]);
            if (v > 255) {
                return null;
            }
            b[i] = (byte) v;
        }
        try {
            return InetAddress.getByAddress(b);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static List<Cidr> parseCidrs(String csv) {
        List<Cidr> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String token : csv.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            Cidr c = Cidr.parse(t);
            if (c != null) {
                out.add(c);
            } else {
                log.warn("Ignoring invalid trusted-proxy CIDR: {}", t);
            }
        }
        return out;
    }

    /** A parsed CIDR block; matches IPv4 and IPv6 without mixing the two. */
    private static final class Cidr {
        private final byte[] network;
        private final int prefix;

        private Cidr(byte[] network, int prefix) {
            this.network = network;
            this.prefix = prefix;
        }

        static Cidr parse(String token) {
            try {
                String ip;
                int prefix;
                int slash = token.indexOf('/');
                if (slash >= 0) {
                    ip = token.substring(0, slash);
                    prefix = Integer.parseInt(token.substring(slash + 1).trim());
                } else {
                    ip = token;
                    prefix = -1;   // bare host == /32 or /128
                }
                byte[] base = InetAddress.getByName(ip.trim()).getAddress();
                if (prefix < 0) {
                    prefix = base.length * 8;
                }
                if (prefix > base.length * 8) {
                    return null;
                }
                return new Cidr(base, prefix);
            } catch (Exception e) {
                return null;
            }
        }

        boolean contains(InetAddress addr) {
            byte[] a = addr.getAddress();
            if (a.length != network.length) {
                return false;   // v4 address vs v6 block (or vice-versa)
            }
            int fullBytes = prefix / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (a[i] != network[i]) {
                    return false;
                }
            }
            int rem = prefix % 8;
            if (rem != 0) {
                int mask = (0xFF << (8 - rem)) & 0xFF;
                return (a[fullBytes] & mask) == (network[fullBytes] & mask);
            }
            return true;
        }
    }
}
