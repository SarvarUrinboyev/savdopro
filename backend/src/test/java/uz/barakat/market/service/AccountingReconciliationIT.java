package uz.barakat.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.domain.Currency;
import uz.barakat.market.domain.PaymentType;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.Shop;
import uz.barakat.market.dto.AccountingDtos.ProfitLossResponse;
import uz.barakat.market.dto.ExpenseRequest;
import uz.barakat.market.dto.PosDtos.CartItem;
import uz.barakat.market.dto.PosDtos.CheckoutRequest;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.ShopRepository;

/**
 * Reconciliation guarantees between POS sales and the accounting reports, on an
 * isolated in-memory DB. Locks in: debt sales hit receivable (not cash), cash
 * sales hit cash; the date filter excludes out-of-range sales; expenses reduce
 * net profit; and an empty shop reports zeros (never a 500/NPE).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:acct_recon_it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "app.demo-seed.enabled=false"
})
class AccountingReconciliationIT {

    @Autowired private PosService pos;
    @Autowired private ExpenseService expenses;
    @Autowired private ProductRepository products;
    @Autowired private ShopRepository shops;
    @Autowired private LedgerBackfillService backfill;
    @Autowired private FinancialStatementService statements;
    @Autowired private JdbcTemplate jdbc;

    private Long shopId;

    @BeforeEach
    void newShop() {
        Shop s = new Shop();
        s.setAccountId(1L);
        s.setName("Acct recon shop");
        shopId = shops.save(s).getId();
        TenantContext.setShopId(shopId);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    // ---- debt sale increases receivable, not cash; cash sale the reverse ----

    @Test
    void debtSaleHitsReceivableAndCashSaleHitsCash() {
        Product p = product("Tovar", 1_000, 2_000, 100);

        checkout(PaymentType.QARZGA, new CartItem(p.getId(), 3, BigDecimal.ZERO, null)); // 6 000 on credit
        backfill.run();
        // Receivable (1400) up by 6 000; cash (1100) untouched.
        assertThat(glBalance("1400")).isEqualByComparingTo("6000");
        assertThat(glBalance("1100")).isEqualByComparingTo("0");

        checkout(PaymentType.NAQD, new CartItem(p.getId(), 2, BigDecimal.ZERO, null)); // 4 000 cash
        backfill.run();
        // Cash (1100) now up by 4 000; receivable unchanged.
        assertThat(glBalance("1100")).isEqualByComparingTo("4000");
        assertThat(glBalance("1400")).isEqualByComparingTo("6000");
    }

    // ---- the report date filter excludes sales outside [from, to] ----

    @Test
    void dateFilterExcludesOutOfRangeSales() {
        Product p = product("Tovar", 1_000, 3_000, 100);
        checkout(PaymentType.NAQD, new CartItem(p.getId(), 1, BigDecimal.ZERO, null)); // today
        backfill.run();

        // A window two months in the past sees nothing.
        LocalDate pastFrom = LocalDate.now().minusMonths(2).withDayOfMonth(1);
        LocalDate pastTo = pastFrom.plusDays(20);
        assertThat(statements.profitLoss(pastFrom, pastTo).revenueTotal()).isEqualByComparingTo("0");

        // Today's window sees the sale.
        assertThat(statements.profitLoss(LocalDate.now().withDayOfMonth(1), LocalDate.now())
                .revenueTotal()).isEqualByComparingTo("3000");
    }

    // ---- expenses reduce net profit ----

    @Test
    void expenseReducesNetProfit() {
        Product p = product("Tovar", 1_000, 2_000, 100);
        checkout(PaymentType.NAQD, new CartItem(p.getId(), 5, BigDecimal.ZERO, null)); // rev 10 000, COGS 5 000
        // Same canonical scale as product prices (see ACCOUNTING_RULES.md).
        expenses.create(new ExpenseRequest(LocalDate.now(), "Komunal", new BigDecimal("2000"),
                PaymentType.NAQD, null, null, null, Currency.USD, null));
        backfill.run();

        ProfitLossResponse pnl = pnl();
        assertThat(pnl.grossProfit()).isEqualByComparingTo("5000");  // 10 000 - 5 000
        assertThat(pnl.netProfit()).isEqualByComparingTo("3000");    // 5 000 - 2 000 expense
    }

    // ---- empty shop reports zeros, never an error ----

    @Test
    void emptyShopReportsZerosSafely() {
        ProfitLossResponse pnl = pnl();
        assertThat(pnl.revenueTotal()).isEqualByComparingTo("0");
        assertThat(pnl.cogsTotal()).isEqualByComparingTo("0");
        assertThat(pnl.grossProfit()).isEqualByComparingTo("0");
        assertThat(pnl.netProfit()).isEqualByComparingTo("0");
        // Balance sheet on an empty shop is trivially balanced.
        assertThat(statements.balanceSheet(LocalDate.now()).balanced()).isTrue();
    }

    // ------------------------------------------------------------ helpers

    private Product product(String name, long cost, long sale, int qty) {
        Product p = new Product();
        p.setName(name);
        p.setPurchasePrice(BigDecimal.valueOf(cost));
        p.setSalePrice(BigDecimal.valueOf(sale));
        p.setQuantity(qty);
        return products.save(p);
    }

    private void checkout(PaymentType method, CartItem item) {
        pos.checkout(new CheckoutRequest(
                List.of(item), BigDecimal.ZERO, BigDecimal.ZERO, method.name(), null, null, null),
                "tester");
    }

    private ProfitLossResponse pnl() {
        return statements.profitLoss(LocalDate.now().withDayOfMonth(1), LocalDate.now());
    }

    /** Net debit balance (debit - credit) of a GL account code for this shop. */
    private BigDecimal glBalance(String code) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(l.debit - l.credit), 0) FROM gl_journal_line l "
                + "JOIN gl_journal_entry e ON e.id = l.entry_id "
                + "JOIN gl_account a ON a.id = l.account_id "
                + "WHERE l.shop_id = ? AND a.code = ? AND e.posted = TRUE",
                BigDecimal.class, shopId, code);
    }
}
