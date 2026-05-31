package uz.barakat.license.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uz.barakat.license.domain.Account;
import uz.barakat.license.domain.AppUser;
import uz.barakat.license.repository.AccountRepository;

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
 *   <li>Default (safe): if {@code savdopro.jwt.secret} is unset, shorter
 *       than 32 chars, equal to the well-known dev fallback string, or
 *       still starts with the {@code CHANGE_ME} placeholder from
 *       {@code .env.example}, the app refuses to start. The operator
 *       must supply a 64+ char random string via the
 *       {@code SAVDOPRO_JWT_SECRET} env var.</li>
 *   <li>Opt-in for development: setting
 *       {@code SAVDOPRO_ALLOW_DEV_SECRET=true} lets the app boot on the
 *       dev fallback with a loud WARN. Never set this on a server that
 *       holds real data.</li>
 * </ul>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** Match the dev default so we can detect it at startup. */
    static final String DEV_FALLBACK_SECRET =
            "savdopro-dev-secret-please-override-in-production-XXXXXXXXXXXXXXXX";

    /**
     * Secrets that still begin with this token look like an un-edited
     * copy of {@code .env.example} — the operator forgot to swap the
     * placeholder for a real value. Compared case-insensitively.
     */
    private static final String PLACEHOLDER_PREFIX = "CHANGE_ME";

    /**
     * Access-token lifetime. Short on purpose (1h) — the desktop client
     * pairs each access token with a refresh token (7d) and silently
     * rotates before expiry, so a leaked access token grants at most
     * one hour of impersonation. Refresh flow is implemented in
     * {@link RefreshTokenService}.
     */
    private static final long TOKEN_TTL_HOURS = 1;

    private final String configuredSecret;
    private final boolean allowDevSecret;
    private final PermissionService permissions;
    private final AccountRepository accounts;
    private SecretKey signingKey;

    public JwtService(@Value("${savdopro.jwt.secret:}") String configuredSecret,
                      @Value("${SAVDOPRO_ALLOW_DEV_SECRET:false}") boolean allowDevSecret,
                      PermissionService permissions,
                      AccountRepository accounts) {
        this.configuredSecret = configuredSecret;
        this.allowDevSecret = allowDevSecret;
        this.permissions = permissions;
        this.accounts = accounts;
    }

    @PostConstruct
    void init() {
        // HS256/HS512 needs at least 32 bytes of key material. We fail closed:
        // if the operator hasn't supplied a strong secret, refuse to start
        // unless SAVDOPRO_ALLOW_DEV_SECRET=true explicitly opts in to the
        // dev fallback (intended for local development only).
        String key = configuredSecret;
        boolean weak = key == null
                || key.length() < 32
                || DEV_FALLBACK_SECRET.equals(key)
                || key.regionMatches(true, 0, PLACEHOLDER_PREFIX, 0, PLACEHOLDER_PREFIX.length());

        if (weak) {
            if (!allowDevSecret) {
                throw new IllegalStateException(
                        "REFUSING TO START: savdopro.jwt.secret is unset, shorter than 32 "
                                + "chars, equal to the dev fallback, or still contains the "
                                + "CHANGE_ME placeholder from .env.example. Generate a 64+ "
                                + "char random string (e.g. `openssl rand -base64 48`) and "
                                + "pass it via the SAVDOPRO_JWT_SECRET env var. To explicitly "
                                + "allow the dev fallback for local development, set "
                                + "SAVDOPRO_ALLOW_DEV_SECRET=true.");
            }
            key = DEV_FALLBACK_SECRET;
            log.warn("=================================================================");
            log.warn("  JWT secret is the DEV FALLBACK — DO NOT USE IN PRODUCTION.");
            log.warn("  SAVDOPRO_ALLOW_DEV_SECRET=true is set; booting anyway.");
            log.warn("  Set SAVDOPRO_JWT_SECRET to a 64+ char random string before deploy.");
            log.warn("=================================================================");
        }

        this.signingKey = Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        Instant exp = now.plus(TOKEN_TTL_HOURS, ChronoUnit.HOURS);
        // Subscription state rides in the token so the shop backend enforces
        // read-only / hard-block with no cross-service call. Re-minted on every
        // refresh, so it stays roughly current. subExp = -1 means "no expiry".
        Account account = accounts.findById(user.getAccountId()).orElse(null);
        long subExp = (account != null && account.getSubscriptionExpires() != null)
                ? account.getSubscriptionExpires().toEpochDay() : -1L;
        boolean blocked = account != null && account.isBlocked();
        int maxShops = account != null ? account.getPlan().maxShops() : Integer.MAX_VALUE;
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .claim("accountId", user.getAccountId())
                // Web authorization: the backend reads this to enforce
                // RESOURCE:ACTION permissions per endpoint with no DB round-trip.
                // Single source of truth — the effective set is computed here.
                .claim("perms", List.copyOf(permissions.effective(user)))
                .claim("subExp", subExp)
                .claim("blk", blocked)
                .claim("maxShops", maxShops)
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
}
