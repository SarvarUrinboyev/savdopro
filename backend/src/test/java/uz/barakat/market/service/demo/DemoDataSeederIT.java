package uz.barakat.market.service.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.dto.AccountingDtos.ProfitLossResponse;
import uz.barakat.market.service.FinancialStatementService;

/**
 * Drives the real {@link DemoDataSeeder} against an ISOLATED in-memory H2
 * (its own datasource URL, so the shared {@code savdopro_test} DB the other
 * 239 tests rely on is never touched). Verifies the seed:
 * <ul>
 *   <li>creates the reserved demo tenants and the full product catalogue,</li>
 *   <li>produces a real, balanced double-entry ledger that reconciles to an
 *       exact P&L (proving sales actually post accounting), and</li>
 *   <li>is idempotent — a second run adds nothing.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:demo_seed_it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "app.demo-seed.enabled=false" // we drive seedOnce() by hand; ApplicationRunner stays off
})
class DemoDataSeederIT {

    // Expected P&L for shop A main (no discounts, single canonical scale):
    //   revenue = 2*11000+3*3500 + 120000+2*9000 + 95000 + 42000+2*12000 = 331 500
    //   COGS    = 2*8900+3*2500  + 95000+2*6000  + 78000 + 32000+2*9500  = 261 300
    //   gross   = 70 200 ; expenses = 20 000 + 25 000 = 45 000 ; net = 25 200
    private static final String REVENUE = "331500";
    private static final String COGS = "261300";
    private static final String GROSS = "70200";
    private static final String NET = "25200";
    private static final long CUSTOMER_DEBT = 330_000L; // debtor 250 000 + partial 80 000

    @Autowired private DemoDataSeeder seeder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FinancialStatementService statements;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void seedsBalancedDemoDataAndIsIdempotent() {
        seeder.seedOnce();

        // --- Tenants & shops in the reserved id band ---
        assertThat(count("SELECT COUNT(*) FROM accounts WHERE id IN (90001, 90002)")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM shops WHERE id IN (90101, 90102, 90201)")).isEqualTo(3);

        // --- Catalogue: 20 products in shop A main, B's are separate ---
        assertThat(count("SELECT COUNT(*) FROM products WHERE shop_id = 90101")).isEqualTo(20);
        assertThat(count("SELECT COUNT(*) FROM products WHERE shop_id = 90201")).isEqualTo(5);

        // --- Sales actually posted a real, balanced ledger that reconciles ---
        TenantContext.setShopId(90101L);
        ProfitLossResponse pnl = statements.profitLoss(LocalDate.now().withDayOfMonth(1), LocalDate.now());
        assertThat(pnl.revenueTotal()).isEqualByComparingTo(REVENUE);
        assertThat(pnl.cogsTotal()).isEqualByComparingTo(COGS);
        assertThat(pnl.grossProfit()).isEqualByComparingTo(GROSS);
        assertThat(pnl.netProfit()).isEqualByComparingTo(NET);
        TenantContext.clear();

        // --- Customer debt ledger populated (debtor + partial-payment) ---
        Long debt = jdbc.queryForObject(
                "SELECT COALESCE(SUM(CASE WHEN type = 'GOODS' THEN amount ELSE -amount END), 0) "
                + "FROM customer_transactions WHERE shop_id = 90101", Long.class);
        assertThat(debt).isEqualTo(CUSTOMER_DEBT);

        // --- Idempotency: a second run is a no-op (the account-exists guard) ---
        long shopsBefore = count("SELECT COUNT(*) FROM shops");
        long productsBefore = count("SELECT COUNT(*) FROM products");
        seeder.seedOnce();
        assertThat(count("SELECT COUNT(*) FROM shops")).isEqualTo(shopsBefore);
        assertThat(count("SELECT COUNT(*) FROM products")).isEqualTo(productsBefore);
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
