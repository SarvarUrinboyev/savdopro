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
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtFilter,
                                           ApiKeyAuthFilter apiKeyFilter)
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
                        // Actuator health/info — public so load balancers can probe;
                        // details are gated (show-details=when-authorized).
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Other actuator endpoints (e.g. /actuator/prometheus) are not
                        // public — scrape them with an authenticated service token.
                        .requestMatchers("/actuator/**").authenticated()
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
                        // Payment provider webhooks (Click / Payme). These
                        // self-authenticate per call: Payme via the Basic
                        // merchant key, Click via the MD5 sign_string. They
                        // carry no JWT, so they are open at the HTTP layer.
                        .requestMatchers("/api/pay/**").permitAll()
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
                        // Procurement (purchase orders, receiving, cost history/valuation)
                        // is a warehouse concern — gate on the PRODUCTS permission.
                        .requestMatchers(HttpMethod.GET, "/api/purchase-orders/**", "/api/costing/**").access(perm("PRODUCTS", "READ"))
                        .requestMatchers("/api/purchase-orders/**").access(perm("PRODUCTS", "WRITE"))
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
                        // Accounting (Bosh kitob) is part of finance — reuses the
                        // MANAGEMENT permission so shop owners / finance staff get
                        // it and cashiers (no MANAGEMENT) are 403'd.
                        .requestMatchers(HttpMethod.GET, "/api/accounting/**").access(perm("MANAGEMENT", "READ"))
                        .requestMatchers("/api/accounting/**").access(perm("MANAGEMENT", "WRITE"))
                        .requestMatchers(HttpMethod.GET, "/api/transfers/**").access(perm("TRANSFERS", "READ"))
                        .requestMatchers("/api/transfers/**").access(perm("TRANSFERS", "WRITE"))
                        .requestMatchers(HttpMethod.GET, "/api/promos/**").access(perm("PROMOS", "READ"))
                        .requestMatchers("/api/promos/**").access(perm("PROMOS", "WRITE"))
                        .requestMatchers(HttpMethod.GET, "/api/shops/**").access(perm("SHOPS", "READ"))
                        .requestMatchers("/api/shops/**").access(perm("SHOPS", "WRITE"))
                        // Clearing shift history is destructive (it can hide cash
                        // discrepancies), so it is owner-only — SHIFTS:ADMIN, which
                        // cashiers do NOT have, not plain SHIFTS:WRITE. Must precede
                        // the general shift rules (first match wins).
                        .requestMatchers(HttpMethod.DELETE, "/api/shifts/history").access(perm("SHIFTS", "ADMIN"))
                        .requestMatchers(HttpMethod.GET, "/api/shifts/**", "/api/balance/**", "/api/terminal/**").access(perm("SHIFTS", "READ"))
                        .requestMatchers("/api/shifts/**", "/api/balance/**", "/api/terminal/**").access(perm("SHIFTS", "WRITE"))
                        // Local data-mutation audit trail — read-only, owner/finance.
                        .requestMatchers("/api/audit/**").access(perm("AUDIT", "READ"))
                        // Acknowledging an anomaly is an owner mutation — must precede the
                        // read-only /api/ai rule below (first match wins), else REPORTS:READ
                        // alone (e.g. a cashier) could clear an alert.
                        .requestMatchers(HttpMethod.POST, "/api/ai/anomalies/*/acknowledge").access(perm("REPORTS", "WRITE"))
                        // AI insights are read-only (the POST /ask is a query, not a mutation).
                        .requestMatchers("/api/ai/**").access(perm("REPORTS", "READ"))
                        .requestMatchers(HttpMethod.GET, "/api/report/**", "/api/dashboard/**", "/api/exchange-rate/**").access(perm("REPORTS", "READ"))
                        .requestMatchers("/api/report/**", "/api/exchange-rate/**").access(perm("REPORTS", "WRITE"))
                        // Integration management (API keys + webhook subscriptions) is an
                        // owner action — gate on SHOPS:WRITE (cashiers lack it). Must precede
                        // the /api/** catch-all.
                        .requestMatchers("/api/integrations/**").access(perm("SHOPS", "WRITE"))
                        // External Open API (/api/v1/**) — authenticated by API key
                        // (ApiKeyAuthFilter sets SCOPE_* authorities); per-resource scope.
                        // JWT users carry no SCOPE_ authority, API keys carry no perms, so
                        // the two auth worlds can't cross over.
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").access(scope("catalog:read"))
                        .requestMatchers(HttpMethod.GET, "/api/v1/sales/**").access(scope("sales:read"))
                        .requestMatchers(HttpMethod.GET, "/api/v1/customers/**").access(scope("customers:read"))
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/**").access(scope("orders:read"))
                        .requestMatchers(HttpMethod.GET, "/api/v1/accounting/**").access(scope("accounting:read"))
                        // /api/v1/me + /api/v1/ping — any valid key.
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
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
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // API-key auth runs BEFORE the JWT filter; it acts only on sk_live_
                // tokens / X-Api-Key and leaves JWT bearer tokens untouched.
                .addFilterBefore(apiKeyFilter, JwtAuthFilter.class);
        return http.build();
    }

    /**
     * Builds an {@link AuthorizationManager} that grants access only when the
     * caller carries the given OAuth-style scope as a {@code SCOPE_<scope>}
     * authority. API keys carry these (set by {@link ApiKeyAuthFilter}); JWT
     * users never do — keeping the external Open API and the first-party app on
     * separate authorization rails.
     */
    private static AuthorizationManager<RequestAuthorizationContext> scope(String scope) {
        String authority = "SCOPE_" + scope;
        return (authentication, context) -> {
            var a = authentication.get();
            boolean ok = a != null && a.isAuthenticated()
                    && a.getAuthorities().stream().anyMatch(g -> authority.equals(g.getAuthority()));
            return new AuthorizationDecision(ok);
        };
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
