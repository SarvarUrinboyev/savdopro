package uz.barakat.license.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import uz.barakat.license.domain.AppUser;

/**
 * Stateless JWT issuer / parser. The desktop client stores the token in
 * localStorage; every API call sends it back as {@code Authorization:
 * Bearer ...}. The token carries the user id, account id and role so the
 * filter doesn't need a DB round-trip on the hot path.
 *
 * <h2>TTL</h2>
 * Tokens live for {@link #TOKEN_TTL_HOURS} hours. The desktop app reuses
 * the same JWT for both the License Server (auth / admin) and the local
 * backend (data) — so we keep the window short enough that a leaked
 * token can't impersonate a user for weeks, but long enough that a
 * cashier doesn't have to log in mid-shift. Refresh-token flow is on
 * the Phase 3 roadmap.
 *
 * <h2>Secret</h2>
 * <ul>
 *   <li>Dev: if {@code savdopro.jwt.secret} is unset, falls back to a
 *       known string and logs a startup WARN.</li>
 *   <li>Prod: if the active Spring profile contains {@code prod} or the
 *       {@code SAVDOPRO_REQUIRE_STRONG_SECRET} env var is true, the
 *       app refuses to start with the dev default — protects against
 *       accidental deployments where the env var wasn't set.</li>
 * </ul>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** Match the dev default so we can detect it at startup. */
    static final String DEV_FALLBACK_SECRET =
            "savdopro-dev-secret-please-override-in-production-XXXXXXXXXXXXXXXX";

    /**
     * Access-token lifetime. Short on purpose (1h) — the desktop client
     * pairs each access token with a refresh token (7d) and silently
     * rotates before expiry, so a leaked access token grants at most
     * one hour of impersonation. Refresh flow is implemented in
     * {@link RefreshTokenService}.
     */
    private static final long TOKEN_TTL_HOURS = 1;

    private final String configuredSecret;
    private final Environment environment;
    private SecretKey signingKey;

    public JwtService(@Value("${savdopro.jwt.secret:}") String configuredSecret,
                      Environment environment) {
        this.configuredSecret = configuredSecret;
        this.environment = environment;
    }

    @PostConstruct
    void init() {
        // HS256/HS512 needs at least 32 bytes of key material.
        String key = configuredSecret;
        boolean usingDevFallback = key == null || key.length() < 32;
        if (usingDevFallback) {
            key = DEV_FALLBACK_SECRET;
        }

        if (usingDevFallback || DEV_FALLBACK_SECRET.equals(key)) {
            if (isProductionProfile()) {
                throw new IllegalStateException(
                        "REFUSING TO START: savdopro.jwt.secret is unset or set to the dev "
                                + "fallback. Generate a 64+ char random string and pass it via "
                                + "the SAVDOPRO_JWT_SECRET env var.");
            }
            log.warn("=================================================================");
            log.warn("  JWT secret is the DEV FALLBACK — DO NOT USE IN PRODUCTION.");
            log.warn("  Set SAVDOPRO_JWT_SECRET to a 64+ char random string before deploy.");
            log.warn("=================================================================");
        }

        this.signingKey = Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(AppUser user) {
        return issueFor(user.getId(), user.getUsername(),
                user.getRole().name(), user.getAccountId());
    }

    /**
     * Lower-level issuer used by the refresh flow — the refresh code
     * already knows the claim values it wants and doesn't need to
     * re-load the AppUser entity just to project them.
     */
    public String issueFor(Long userId, String username, String role, Long accountId) {
        Instant now = Instant.now();
        Instant exp = now.plus(TOKEN_TTL_HOURS, ChronoUnit.HOURS);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("accountId", accountId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey)
                .compact();
    }

    /** Lets the controller layer expose the access-token TTL to clients. */
    public long accessTtlSeconds() {
        return TOKEN_TTL_HOURS * 3600L;
    }

    /** Returns the JWT claims if the token is valid, else throws. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isProductionProfile() {
        // Either Spring's active profile contains "prod" / "production",
        // or the operator explicitly opts in via an env var.
        for (String p : environment.getActiveProfiles()) {
            String low = p.toLowerCase();
            if (low.contains("prod")) return true;
        }
        return "true".equalsIgnoreCase(
                environment.getProperty("SAVDOPRO_REQUIRE_STRONG_SECRET", "false"));
    }
}
