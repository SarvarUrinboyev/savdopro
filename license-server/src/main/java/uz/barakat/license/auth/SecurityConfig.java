package uz.barakat.license.auth;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security wiring for the central License Server.
 *
 * <ul>
 *   <li>JWT bearer auth on every {@code /api/*} call except
 *       {@code /api/auth/login} and {@code /api/health}.</li>
 *   <li>CORS open for every origin — desktop clients are local
 *       loopback (http://127.0.0.1:8086) and the in-app admin panel
 *       runs on the customer's machine, so each call lands here
 *       from a different origin.</li>
 *   <li>CSRF disabled (stateless REST), form login disabled.</li>
 *   <li>{@code @PreAuthorize} enforced on admin endpoints.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtFilter,
                                           MetricsScrapeTokenFilter metricsFilter)
            throws Exception {
        http
                .cors(c -> c.configurationSource(corsSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Response security headers. X-Frame-Options=DENY and
                // X-Content-Type-Options=nosniff are Spring defaults and stay on.
                // HSTS emits once the request is seen as HTTPS behind nginx
                // (set FORWARD_HEADERS_STRATEGY=framework in prod). No CSP here:
                // the signup/OAuth pages this server can render embed the Google /
                // Facebook / Telegram login widgets, and a tight CSP would break
                // them — CSP for the merchant SPA lives in the backend instead.
                .headers(headers -> headers
                        .referrerPolicy(rp -> rp.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000L))
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Permissions-Policy",
                                "camera=(), microphone=(), geolocation=()")))
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/social/google").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/social/facebook").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/social/x").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/telegram").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/signup/config").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup/request-otp").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/forgot-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/billing/webhook").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/billing/click/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/billing/payme").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .requestMatchers("/api/health/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").authenticated()
                        .requestMatchers("/", "/index.html", "/assets/**",
                                "/favicon.ico", "/icon.svg").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // Metrics scrape token runs AFTER the JWT filter: JwtAuthFilter
                // clears the context when a bearer token fails to parse as a JWT
                // (which the opaque scrape token always does), so this filter has
                // to authenticate the scraper afterwards.
                .addFilterAfter(metricsFilter, JwtAuthFilter.class);
        return http.build();
    }

    private static CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        // Permissive on purpose — license server is read-only auth and the
        // calling apps are desktop clients on arbitrary loopback ports.
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Authorization"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
