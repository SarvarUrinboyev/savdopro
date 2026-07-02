package uz.barakat.market.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.barakat.market.repository.AccountRepository;

/**
 * Parses the {@code Authorization: Bearer ...} header, validates the
 * JWT, and stashes the user id + role on the request so controllers
 * can pick them up via {@link HttpServletRequest#getAttribute}.
 *
 * <p>Also rejects blocked / expired accounts mid-session: even if the
 * token is valid, an account that was locked by the super-admin while
 * the user was logged in is bounced out on the next API call.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    public static final String ATTR_USER_ID = "savdopro.userId";
    public static final String ATTR_ACCOUNT_ID = "savdopro.accountId";
    public static final String ATTR_ROLE = "savdopro.role";
    public static final String ATTR_MAX_SHOPS = "savdopro.maxShops";
    /** The JWT's username — used to stamp the cashier on each POS sale. */
    public static final String ATTR_USERNAME = "savdopro.username";

    private final JwtService jwt;
    private final AuthService auth;
    private final AccountRepository accounts;
    private final int graceDays;

    public JwtAuthFilter(JwtService jwt, AuthService auth, AccountRepository accounts,
                         @org.springframework.beans.factory.annotation.Value(
                                 "${savdopro.subscription.grace-days:3}") int graceDays) {
        this.jwt = jwt;
        this.auth = auth;
        this.accounts = accounts;
        this.graceDays = Math.max(0, graceDays);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            // An API-key bearer token (sk_live_…) is handled by ApiKeyAuthFilter,
            // which runs first and may already have set the authentication. Skip
            // it here — never try to parse it as a JWT (that would clear the
            // context and wipe the API-key auth).
            if (token.startsWith(ApiKeyService.TOKEN_PREFIX)) {
                chain.doFilter(request, response);
                return;
            }
            try {
                Claims claims = jwt.parse(token);
                String subject = claims.getSubject();
                if (subject == null || subject.isBlank()) {
                    log.warn("JWT missing subject on path={} remote={}",
                            request.getRequestURI(), request.getRemoteAddr());
                    SecurityContextHolder.clearContext();
                    chain.doFilter(request, response);
                    return;
                }
                Long userId = Long.parseLong(subject);
                Long accountId = claims.get("accountId", Long.class);
                String role = claims.get("role", String.class);
                // Phase 2 mirror: the local accounts table only had id 1
                // (the bundled super-admin). When a JWT arrives for an
                // accountId minted on the License Server, ensure a local
                // stub exists so the shops.account_id FK and downstream
                // joins succeed. INSERT IF NOT EXISTS via native SQL —
                // safe to call on every request, costs one row check.
                if (accountId != null) {
                    String stubName = claims.get("username", String.class);
                    if (stubName == null || stubName.isBlank()) {
                        stubName = "Account #" + accountId;
                    }
                    accounts.insertStubIfAbsent(accountId, stubName);
                }
                // Subscription enforcement from the (live-ish) JWT claims that
                // the License Server mints. A manual super-admin block is a hard
                // stop; a lapsed subscription is soft — reads + the billing page
                // stay open, but mutating calls are refused so the owner is
                // nudged to renew. Tokens minted before this claim existed carry
                // no subExp/blk and are simply not enforced until the next
                // refresh re-mints them.
                if (Boolean.TRUE.equals(claims.get("blk", Boolean.class))) {
                    writeForbidden(response, "BLOCKED",
                            "Akkaunt bloklangan. Super-admin bilan bog'laning.");
                    return;
                }
                Object subExpClaim = claims.get("subExp");
                long subExp = (subExpClaim instanceof Number num) ? num.longValue() : -1L;
                // Grace window: writes survive `grace-days` past expiry so a
                // shop isn't bricked mid-day the moment the clock rolls over —
                // dunning SMS (license server) nudges renewal during it. After
                // the window, mutating verbs are refused; reads stay open so
                // the owner can always reach reports + the billing page.
                boolean pastGrace = subExp >= 0
                        && subExp + graceDays < LocalDate.now().toEpochDay();
                if (pastGrace && isMutating(request.getMethod())) {
                    writeForbidden(response, "SUBSCRIPTION_EXPIRED",
                            "Obuna muddati tugagan. Davom etish uchun tarifni yangilang.");
                    return;
                }
                request.setAttribute(ATTR_USER_ID, userId);
                request.setAttribute(ATTR_ACCOUNT_ID, accountId);
                request.setAttribute(ATTR_ROLE, role);
                request.setAttribute(ATTR_USERNAME, claims.get("username", String.class));
                Object maxShopsClaim = claims.get("maxShops");
                request.setAttribute(ATTR_MAX_SHOPS,
                        maxShopsClaim instanceof Number msNum ? msNum.intValue() : Integer.MAX_VALUE);
                // Authorities = the role (ROLE_*) plus every RESOURCE:ACTION
                // permission the License Server minted into the token. The
                // backend enforces these per endpoint (see SecurityConfig). A
                // legacy token with no "perms" claim authenticates but carries
                // no permissions — refreshing the session re-mints it with the
                // full effective set.
                List<GrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority(
                        "ROLE_" + (role == null || role.isBlank() ? "USER" : role)));
                Object permsClaim = claims.get("perms");
                if (permsClaim instanceof List<?> perms) {
                    for (Object p : perms) {
                        if (p != null) {
                            authorities.add(new SimpleGrantedAuthority(p.toString()));
                        }
                    }
                }
                var authToken = new UsernamePasswordAuthenticationToken(
                        userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (Exception ex) {
                // Invalid / expired token — clear the security context and let
                // Spring Security reject downstream. We log at DEBUG so the
                // common "expired token" case doesn't spam WARN, but always
                // emit at least an audit trail.
                log.debug("JWT rejected on path={} remote={}: {}",
                        request.getRequestURI(), request.getRemoteAddr(), ex.toString());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean isMutating(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private static void writeForbidden(HttpServletResponse response, String code, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
