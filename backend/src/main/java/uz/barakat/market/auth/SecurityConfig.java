package uz.barakat.market.auth;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security wiring for the desktop's local backend.
 *
 * <p>Phase 2: login is no longer served here — it lives on the central
 * License Server. The local backend only validates JWTs issued upstream
 * (shared HMAC secret) and serves tenant-scoped data endpoints. As a
 * result the only public paths are health probes and the SPA shell.
 *
 * <p>CSRF is disabled (stateless REST) and form login is disabled (the
 * React SPA owns the login UI and posts to the License Server directly).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtFilter)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg
                        // FORWARD/INCLUDE dispatches (e.g. from SpaController) are
                        // always permitted — prevents StackOverflowError in Spring
                        // Security 6 when the filter chain re-processes a forward.
                        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.INCLUDE).permitAll()
                        // Public endpoints — health probe and static shell.
                        .requestMatchers("/api/health/**").permitAll()
                        // WebSocket handshake — STOMP CONNECT frame can carry
                        // the JWT in its native auth header; we don't gate it
                        // at the HTTP layer.
                        .requestMatchers("/ws/**", "/ws-sockjs/**").permitAll()
                        // OpenAPI / Swagger UI — public REST docs.
                        .requestMatchers(
                                "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Static frontend bundle.
                        .requestMatchers("/", "/index.html", "/assets/**",
                                "/favicon.ico", "/icon.svg").permitAll()
                        // --- Per-resource RESOURCE:ACTION authorization ---
                        // The required permission is carried in the JWT (minted
                        // by the License Server). Convention: GET = READ, any
                        // mutating verb = WRITE. Wildcards (e.g. SUPER_ADMIN's
                        // "*:*") are honoured by PermissionChecker. Anonymous
                        // callers fall through to a 401 (handled by Spring's
                        // ExceptionTranslationFilter), authenticated-but-
                        // unpermitted callers get a 403.
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").access(perm("PRODUCTS", "READ"))
                        .requestMatchers("/api/products/**", "/api/categories/**").access(perm("PRODUCTS", "WRITE"))
                        .requestMatchers(HttpMethod.GET, "/api/orders/**").access(perm("ORDERS", "READ"))
                        .requestMatchers("/api/orders/**").access(perm("ORDERS", "WRITE"))
                        .requestMatchers(HttpMethod.GET, "/api/debts/**", "/api/debtors/**", "/api/customer-debts/**").access(perm("DEBTS", "READ"))
                        .requestMatchers("/api/debts/**", "/api/debtors/**", "/api/customer-debts/**").access(perm("DEBTS", "WRITE"))
                        .requestMatchers(HttpMethod.GET, "/api/pos/**", "/api/print/**").access(perm("SALES", "READ"))
                        .requestMatchers("/api/pos/**", "/api/print/**").access(perm("SALES", "WRITE"))
                        .requestMatchers(HttpMethod.GET, "/api/payments/**").access(perm("PAYMENTS", "READ"))
                        .requestMatchers("/api/payments/**").access(perm("PAYMENTS", "WRITE"))
                        .requestMatchers(HttpMethod.GET, "/api/customers/**").access(perm("CUSTOMERS", "READ"))
                        .requestMatchers("/api/customers/**").access(perm("CUSTOMERS", "WRITE"))
                        .requestMatchers(HttpMethod.GET, "/api/suppliers/**").access(perm("SUPPLIERS", "READ"))
                        .requestMatchers("/api/suppliers/**").access(perm("SUPPLIERS", "WRITE"))
                        .requestMatchers(HttpMethod.GET, "/api/expenses/**", "/api/home-expenses/**").access(perm("EXPENSES", "READ"))
                        .requestMatchers("/api/expenses/**", "/api/home-expenses/**").access(perm("EXPENSES", "WRITE"))
                        .requestMatchers(HttpMethod.GET, "/api/management/**").access(perm("MANAGEMENT", "READ"))
                        .requestMatchers("/api/management/**").access(perm("MANAGEMENT", "WRITE"))
                        .requestMatchers(HttpMethod.GET, "/api/transfers/**").access(perm("TRANSFERS", "READ"))
                        .requestMatchers("/api/transfers/**").access(perm("TRANSFERS", "WRITE"))
                        .requestMatchers(HttpMethod.GET, "/api/promos/**").access(perm("PROMOS", "READ"))
                        .requestMatchers("/api/promos/**").access(perm("PROMOS", "WRITE"))
                        .requestMatchers(HttpMethod.GET, "/api/shops/**").access(perm("SHOPS", "READ"))
                        .requestMatchers("/api/shops/**").access(perm("SHOPS", "WRITE"))
                        .requestMatchers(HttpMethod.GET, "/api/shifts/**", "/api/balance/**", "/api/terminal/**").access(perm("SHIFTS", "READ"))
                        .requestMatchers("/api/shifts/**", "/api/balance/**", "/api/terminal/**").access(perm("SHIFTS", "WRITE"))
                        // AI insights are read-only (the POST /ask is a query, not a mutation).
                        .requestMatchers("/api/ai/**").access(perm("REPORTS", "READ"))
                        .requestMatchers(HttpMethod.GET, "/api/report/**", "/api/dashboard/**", "/api/exchange-rate/**").access(perm("REPORTS", "READ"))
                        .requestMatchers("/api/report/**", "/api/exchange-rate/**").access(perm("REPORTS", "WRITE"))
                        // Everything else under /api requires a valid JWT.
                        .requestMatchers("/api/**").authenticated()
                        // Anything else (SPA deep-links) is served as-is.
                        .anyRequest().permitAll())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                res.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                        // Authenticated but lacking the required permission. A
                        // distinct JSON body + code lets the SPA show "ruxsat
                        // yo'q" WITHOUT treating it as a dead session (a 401).
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write(
                                    "{\"message\":\"Bu amal uchun ruxsatingiz yo'q\",\"code\":\"FORBIDDEN\"}");
                        }))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Builds an {@link AuthorizationManager} that grants access only when the
     * caller's JWT carries the given {@code RESOURCE:ACTION} permission (with
     * wildcard support via {@link PermissionChecker}). Used by the URL rules
     * above.
     */
    private static AuthorizationManager<RequestAuthorizationContext> perm(
            String resource, String action) {
        return (authentication, context) -> new AuthorizationDecision(
                PermissionChecker.hasPermission(authentication.get(), resource, action));
    }
}
