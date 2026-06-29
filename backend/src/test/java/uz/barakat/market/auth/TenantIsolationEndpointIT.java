package uz.barakat.market.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.Shop;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.ShopRepository;

/**
 * Proves SavdoPRO is a real multi-tenant SaaS at the HTTP boundary: account A
 * cannot read account B's data by pointing the {@code X-Shop-Id} header at B's
 * shop, nor by guessing B's row ids. Runs through the real Spring Security +
 * tenant filter chain with minted JWTs, on an isolated in-memory DB.
 *
 * Two tenants: A (account 90011, shop {@code shopA}) and B (account 90012,
 * shop {@code shopB}), each with one distinctly-named product.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tenant_iso_it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "app.demo-seed.enabled=false"
})
class TenantIsolationEndpointIT {

    /** Must match savdopro.jwt.secret in application-test.properties. */
    private static final String SECRET = "test-only-jwt-secret-not-for-production-0123456789abcdef";

    private static final long ACCOUNT_A = 90_011L;
    private static final long ACCOUNT_B = 90_012L;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ShopRepository shops;
    @Autowired private ProductRepository products;

    private long shopA;
    private long shopB;
    private long productB;

    @BeforeEach
    void seedTwoTenants() {
        account(ACCOUNT_A, "ISO Account A");
        account(ACCOUNT_B, "ISO Account B");
        shopA = shop(ACCOUNT_A, "ISO Shop A");
        shopB = shop(ACCOUNT_B, "ISO Shop B");
        product(shopA, "ISO-A-PRODUCT");
        productB = product(shopB, "ISO-B-PRODUCT");
        TenantContext.clear();
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    // ---- A, scoped to its own shop, sees only its own products ----

    @Test
    void ownerSeesOnlyOwnShopProducts() throws Exception {
        mvc.perform(get("/api/products")
                        .header("Authorization", bearer(ACCOUNT_A, "ACCOUNT_OWNER", "PRODUCTS:READ"))
                        .header("X-Shop-Id", String.valueOf(shopA)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ISO-A-PRODUCT")))
                .andExpect(content().string(not(containsString("ISO-B-PRODUCT"))));
    }

    // ---- A pointing X-Shop-Id at B's shop is rejected at the boundary ----

    @Test
    void crossTenantShopHeaderIsForbidden() throws Exception {
        mvc.perform(get("/api/products")
                        .header("Authorization", bearer(ACCOUNT_A, "ACCOUNT_OWNER", "PRODUCTS:READ"))
                        .header("X-Shop-Id", String.valueOf(shopB)))
                .andExpect(status().isForbidden());
    }

    @Test
    void reverseCrossTenantShopHeaderIsForbidden() throws Exception {
        mvc.perform(get("/api/products")
                        .header("Authorization", bearer(ACCOUNT_B, "ACCOUNT_OWNER", "PRODUCTS:READ"))
                        .header("X-Shop-Id", String.valueOf(shopA)))
                .andExpect(status().isForbidden());
    }

    // ---- A cannot fetch B's product by id even from A's own (valid) shop ----

    @Test
    void cannotFetchOtherTenantRowByIdFromOwnShop() throws Exception {
        mvc.perform(get("/api/products/{id}", productB)
                        .header("Authorization", bearer(ACCOUNT_A, "ACCOUNT_OWNER", "PRODUCTS:READ"))
                        .header("X-Shop-Id", String.valueOf(shopA)))
                .andExpect(status().isNotFound());
    }

    // ---- /api/shops only ever lists the caller's own shops ----

    @Test
    void shopListIsScopedToOwnAccount() throws Exception {
        mvc.perform(get("/api/shops")
                        .header("Authorization", bearer(ACCOUNT_A, "ACCOUNT_OWNER", "SHOPS:READ")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ISO Shop A")))
                .andExpect(content().string(not(containsString("ISO Shop B"))));
    }

    // ---- the boundary check is uniform: every tenant-scoped module rejects B ----

    @Test
    void crossTenantForbiddenAcrossAllModules() throws Exception {
        // A broad read token so authorization passes and the 403 we observe is
        // the TenantFilter ownership check (which runs before the controller),
        // not a missing-permission denial. Each path points at B's shop.
        String broad = bearer(ACCOUNT_A, "ACCOUNT_OWNER",
                "PRODUCTS:READ", "CUSTOMERS:READ", "PAYMENTS:READ", "DEBTS:READ",
                "SALES:READ", "EXPENSES:READ", "TRANSFERS:READ", "ORDERS:READ",
                "MANAGEMENT:READ", "REPORTS:READ", "SHOPS:READ", "SHOPS:WRITE");
        List<String> tenantScopedPaths = List.of(
                "/api/products",
                "/api/customers",
                "/api/payments",
                "/api/debts",
                "/api/devices",
                "/api/expenses",
                "/api/transfers",
                "/api/orders",
                "/api/management/summary",
                "/api/integrations/api-keys");
        for (String path : tenantScopedPaths) {
            mvc.perform(get(path)
                            .header("Authorization", broad)
                            .header("X-Shop-Id", String.valueOf(shopB)))
                    .andExpect(status().isForbidden());
        }
    }

    // ------------------------------------------------------------ helpers

    private void account(long id, String name) {
        jdbc.update("INSERT INTO accounts (id, name, blocked, created_at) "
                + "SELECT ?, ?, FALSE, now() WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE id = ?)",
                id, name, id);
    }

    private long shop(long accountId, String name) {
        Shop s = new Shop();
        s.setAccountId(accountId);
        s.setName(name);
        s.setMain(true);
        return shops.save(s).getId();
    }

    private long product(long shopId, String name) {
        TenantContext.setShopId(shopId);
        Product p = new Product();
        p.setName(name);
        p.setPurchasePrice(BigDecimal.ONE);
        p.setSalePrice(new BigDecimal("2"));
        p.setQuantity(5);
        long id = products.save(p).getId();
        TenantContext.clear();
        return id;
    }

    private static String bearer(long accountId, String role, String... perms) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return "Bearer " + Jwts.builder()
                .subject("1")
                .claim("username", "iso-tester")
                .claim("role", role)
                .claim("accountId", accountId)
                .claim("perms", List.of(perms))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }
}
