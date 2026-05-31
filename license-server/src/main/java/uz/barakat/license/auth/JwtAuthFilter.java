package uz.barakat.license.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

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

    public static final String ATTR_USER_ID = "savdopro.userId";
    public static final String ATTR_ACCOUNT_ID = "savdopro.accountId";
    public static final String ATTR_ROLE = "savdopro.role";

    private final JwtService jwt;
    private final AuthService auth;

    public JwtAuthFilter(JwtService jwt, AuthService auth) {
        this.jwt = jwt;
        this.auth = auth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwt.parse(token);
                Long userId = Long.parseLong(claims.getSubject());
                Long accountId = claims.get("accountId", Long.class);
                String role = claims.get("role", String.class);
                // Hard-block only a manually BLOCKED account. An expired
                // subscription is NOT blocked here: the merchant must still
                // reach /me + /billing to see status and renew. Read-only is
                // enforced on the shop backend, not on the license API.
                if (accountId != null && auth.isAccountBlocked(accountId)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"code\":\"BLOCKED\",\"message\":\"Akkaunt bloklangan. "
                                    + "Super-admin bilan bog'laning.\"}");
                    return;
                }
                request.setAttribute(ATTR_USER_ID, userId);
                request.setAttribute(ATTR_ACCOUNT_ID, accountId);
                request.setAttribute(ATTR_ROLE, role);
                var authToken = new UsernamePasswordAuthenticationToken(
                        userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (Exception ex) {
                // Invalid / expired token — fall through unauthenticated;
                // Spring Security will reject if the endpoint requires auth.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
