package uz.barakat.license.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Authorization behaviour of the super-admin API.
 *
 * <p>Regression guard for the audit finding "normal user → admin API returns
 * 500 instead of 403". The {@code @PreAuthorize("hasRole('SUPER_ADMIN')")} on
 * {@link AdminController} throws an {@code AccessDeniedException} for a
 * non-admin; before the fix the catch-all {@code @ExceptionHandler(Exception)}
 * swallowed it into a 500. These tests mint real JWTs (signed with the test
 * profile secret, exactly like {@link JwtService}) so the whole chain runs:
 * {@code JwtAuthFilter → method security → GlobalExceptionHandler}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${savdopro.jwt.secret}")
    private String jwtSecret;

    /** Mint a valid bearer token carrying the given role (no accountId so the
     *  filter's blocked-account DB check is skipped — we only test authz). */
    private String tokenFor(String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("1")
                .claim("username", role.toLowerCase())
                .claim("role", role)
                .signWith(key)
                .compact();
    }

    @Test
    void nonAdminGetsForbiddenNotServerError() throws Exception {
        mockMvc.perform(get("/api/admin/accounts")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isForbidden())
                // Safe JSON body, no leaked stack trace.
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void managerRoleStillForbiddenFromAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/accounts")
                        .header("Authorization", "Bearer " + tokenFor("MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminIsAuthorized() throws Exception {
        mockMvc.perform(get("/api/admin/accounts")
                        .header("Authorization", "Bearer " + tokenFor("SUPER_ADMIN")))
                .andExpect(status().isOk());
    }
}
