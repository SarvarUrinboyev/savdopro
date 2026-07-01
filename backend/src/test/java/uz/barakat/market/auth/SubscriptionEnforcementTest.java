package uz.barakat.market.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Subscription enforcement via the JWT claims the License Server mints:
 *   - a lapsed subscription is READ-ONLY once the grace window (default
 *     3 days, savdopro.subscription.grace-days) has passed — GETs pass but
 *     mutating verbs are refused with code SUBSCRIPTION_EXPIRED;
 *   - within the grace window writes still work (dunning nudges renewal);
 *   - a manual block (blk=true) is a hard stop on every verb.
 * All tokens carry {@code *:*} so authorization never decides the outcome —
 * only the subscription gate in JwtAuthFilter does.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubscriptionEnforcementTest {

    /** Must match savdopro.jwt.secret in application-test.properties. */
    private static final String SECRET =
            "test-only-jwt-secret-not-for-production-0123456789abcdef";

    private static final long YESTERDAY = LocalDate.now().minusDays(1).toEpochDay();
    /** Past the default 3-day grace window (expired 4 days ago). */
    private static final long PAST_GRACE = LocalDate.now().minusDays(4).toEpochDay();
    private static final long TOMORROW = LocalDate.now().plusDays(1).toEpochDay();

    @Autowired
    private MockMvc mvc;

    @Test
    void expiredPastGraceBlocksWrites() throws Exception {
        mvc.perform(delete("/api/shifts/history")
                        .header("Authorization", token(PAST_GRACE, false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_EXPIRED"));
    }

    @Test
    void expiredWithinGraceStillAllowsWrites() throws Exception {
        mvc.perform(delete("/api/shifts/history")
                        .header("Authorization", token(YESTERDAY, false)))
                .andExpect(status().isOk());
    }

    @Test
    void expiredSubscriptionStillAllowsReads() throws Exception {
        mvc.perform(get("/api/management/summary")
                        .header("Authorization", token(PAST_GRACE, false)))
                .andExpect(status().isOk());
    }

    @Test
    void blockedAccountIsHardStoppedEvenOnReads() throws Exception {
        mvc.perform(get("/api/management/summary")
                        .header("Authorization", token(TOMORROW, true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BLOCKED"));
    }

    @Test
    void activeSubscriptionAllowsWrites() throws Exception {
        mvc.perform(delete("/api/shifts/history")
                        .header("Authorization", token(TOMORROW, false)))
                .andExpect(status().isOk());
    }

    private static String token(long subExp, boolean blocked) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String jwt = Jwts.builder()
                .subject("1")
                .claim("username", "tester")
                .claim("role", "ACCOUNT_OWNER")
                .claim("accountId", 1L)
                .claim("perms", List.of("*:*"))
                .claim("subExp", subExp)
                .claim("blk", blocked)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
        return "Bearer " + jwt;
    }
}
