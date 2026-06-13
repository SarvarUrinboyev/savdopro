package uz.barakat.market.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.barakat.market.domain.ApiKey;

/**
 * Authenticates external Open-API ({@code /api/v1/**}) callers by API key.
 *
 * <p>Runs <strong>before</strong> {@link JwtAuthFilter}. It acts only when the
 * credential is an API key — {@code Authorization: Bearer sk_live_…} or the
 * {@code X-Api-Key} header — and never when a JWT bearer token is present (that
 * is left to {@code JwtAuthFilter}). On a valid, active, unexpired key it sets a
 * Spring {@code Authentication} whose authorities are the key's scopes as
 * {@code SCOPE_<scope>}, and stashes the key id + shop id on the request so
 * {@link TenantFilter} can scope the tenant to the key's shop (external callers
 * send no {@code X-Shop-Id}). An unknown/disabled/expired key sets no auth, so
 * Spring Security rejects the call with 401.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);

    public static final String ATTR_API_KEY_ID = "savdopro.apiKeyId";
    public static final String ATTR_API_KEY_SHOP_ID = "savdopro.apiKeyShopId";
    private static final String BEARER = "Bearer ";

    private final ApiKeyService apiKeys;

    public ApiKeyAuthFilter(ApiKeyService apiKeys) {
        this.apiKeys = apiKeys;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String secret = extractApiKey(request);
        if (secret != null) {
            try {
                ApiKey key = apiKeys.resolve(secret).orElse(null);
                if (isUsable(key)) {
                    List<String> scopes = apiKeys.scopesOf(key);
                    var authorities = scopes.stream()
                            .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                            .toList();
                    var auth = new UsernamePasswordAuthenticationToken(
                            "apikey:" + key.getId(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    request.setAttribute(ATTR_API_KEY_ID, key.getId());
                    request.setAttribute(ATTR_API_KEY_SHOP_ID, key.getShopId());
                    touchIfStale(key);
                } else {
                    // Present but unknown/disabled/expired — leave context empty so
                    // Spring Security returns 401. Log at DEBUG to avoid noise.
                    log.debug("Rejected API key on path={} remote={}",
                            request.getRequestURI(), request.getRemoteAddr());
                    SecurityContextHolder.clearContext();
                }
            } catch (RuntimeException ex) {
                log.warn("API key auth error on path={}: {}", request.getRequestURI(), ex.toString());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Returns the API-key secret if (and only if) the request carries one and
     * NOT a JWT. A {@code Bearer} token that is not an API key is a JWT — we
     * return null so {@code JwtAuthFilter} handles it and we never touch it.
     */
    private static String extractApiKey(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length());
            return token.startsWith(ApiKeyService.TOKEN_PREFIX) ? token : null; // else: JWT
        }
        String x = request.getHeader("X-Api-Key");
        if (x != null && !x.isBlank()) {
            return x.trim();
        }
        return null;
    }

    private static boolean isUsable(ApiKey key) {
        return key != null
                && key.isActive()
                && (key.getExpiresAt() == null || key.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    private void touchIfStale(ApiKey key) {
        LocalDateTime last = key.getLastUsedAt();
        if (last == null || last.isBefore(LocalDateTime.now().minusMinutes(5))) {
            try {
                apiKeys.touchLastUsed(key.getId());
            } catch (RuntimeException ignored) {
                // best-effort; never fail a request over a usage timestamp
            }
        }
    }
}
