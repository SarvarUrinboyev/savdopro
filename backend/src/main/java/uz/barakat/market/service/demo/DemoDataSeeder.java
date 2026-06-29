package uz.barakat.market.service.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.domain.Category;
import uz.barakat.market.domain.Currency;
import uz.barakat.market.domain.Customer;
import uz.barakat.market.domain.CustomerTransaction;
import uz.barakat.market.domain.CustomerTxType;
import uz.barakat.market.domain.PaymentType;
import uz.barakat.market.domain.Product;
import uz.barakat.market.dto.ExpenseRequest;
import uz.barakat.market.dto.PosDtos.CartItem;
import uz.barakat.market.dto.PosDtos.CheckoutRequest;
import uz.barakat.market.repository.CategoryRepository;
import uz.barakat.market.repository.CustomerRepository;
import uz.barakat.market.repository.CustomerTransactionRepository;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.service.ExpenseService;
import uz.barakat.market.service.LedgerBackfillService;
import uz.barakat.market.service.PosService;
import uz.barakat.market.service.TransferService;
import uz.barakat.market.service.TransferService.TransferRequest;

/**
 * Idempotent, guarded staging/demo data so the product can be exercised
 * end-to-end (dashboard, warehouse, POS, customers, debt, accounting,
 * reports) and so tenant A/B isolation can be demonstrated on a live box.
 *
 * <h2>Safety — never runs in production</h2>
 * Seeding is enabled ONLY when {@code app.demo-seed.enabled=true}
 * (backed by the {@code ALLOW_DEMO_SEED} env var) or the active Spring
 * profile contains {@code dev}/{@code staging}. It is HARD-DISABLED when
 * the active profile contains {@code prod} or {@code test}. A failure
 * during seeding is logged and swallowed so it can never block startup.
 *
 * <h2>Idempotency</h2>
 * All demo tenants live in a reserved high id band (accounts 90001/90002,
 * shops 90101/90102/90201) inserted with an explicit-id {@code INSERT …
 * WHERE NOT EXISTS} guard. If demo account A (90001) already exists the
 * whole run is skipped, so a restart never duplicates rows and never
 * touches real (non-demo) tenants.
 *
 * <h2>Why it calls the real services</h2>
 * Sales go through {@link PosService#checkout} and expenses through
 * {@link ExpenseService#create} rather than raw inserts, so the same
 * stock movements, payment-journal rows and double-entry ledger postings
 * a real cashier would create are produced — the accounting reports are
 * therefore real, not faked.
 */
@Component
@Order(Integer.MAX_VALUE) // run after every other ApplicationRunner / migration
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** Reserved demo id band — far above any real tenant, so it never collides. */
    static final long ACCOUNT_A = 90_001L;
    static final long ACCOUNT_B = 90_002L;
    static final long SHOP_A_MAIN = 90_101L;   // Barokat Demo — Markaziy
    static final long SHOP_A_BRANCH = 90_102L; // Barokat Demo — Filial
    static final long SHOP_B_MAIN = 90_201L;   // Raqobatchi Demo Do'kon

    private final Environment env;
    private final JdbcTemplate jdbc;
    private final boolean propEnabled;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final CustomerRepository customers;
    private final CustomerTransactionRepository customerTx;
    private final PosService pos;
    private final ExpenseService expenses;
    private final TransferService transfers;
    private final LedgerBackfillService ledgerBackfill;

    public DemoDataSeeder(Environment env, JdbcTemplate jdbc,
                          @Value("${app.demo-seed.enabled:false}") boolean propEnabled,
                          ProductRepository products, CategoryRepository categories,
                          CustomerRepository customers, CustomerTransactionRepository customerTx,
                          PosService pos, ExpenseService expenses, TransferService transfers,
                          LedgerBackfillService ledgerBackfill) {
        this.env = env;
        this.jdbc = jdbc;
        this.propEnabled = propEnabled;
        this.products = products;
        this.categories = categories;
        this.customers = customers;
        this.customerTx = customerTx;
        this.pos = pos;
        this.expenses = expenses;
        this.transfers = transfers;
        this.ledgerBackfill = ledgerBackfill;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled()) {
            log.debug("Demo seed disabled (set ALLOW_DEMO_SEED=true in dev/staging to enable).");
            return;
        }
        try {
            seedOnce();
        } catch (RuntimeException ex) {
            // A demo-seed failure must never stop the app from starting.
            log.error("Demo seed failed (startup continues): {}", ex.toString(), ex);
        } finally {
            TenantContext.clear();
        }
    }

    /** Idempotency guard: seed the demo tenants once; a second call is a no-op. */
    void seedOnce() {
        if (accountExists(ACCOUNT_A)) {
            log.info("Demo data already present (account {}) — skipping seed.", ACCOUNT_A);
            return;
        }
        log.info("ALLOW_DEMO_SEED on — seeding guarded demo/staging data …");
        seedAll();
        log.info("Demo seed complete. Accounts A={} B={}; shops A-main={} A-branch={} B={}.",
                ACCOUNT_A, ACCOUNT_B, SHOP_A_MAIN, SHOP_A_BRANCH, SHOP_B_MAIN);
    }

    /** The actual seeding work (no guard) — split out so tests can drive it. */
    void seedAll() {
        seedTenants();
        seedAccountAMain();
        seedAccountABranch();
        seedAccountB();
    }

    /** Enabled in dev/staging or via ALLOW_DEMO_SEED; never in prod/test. */
    boolean enabled() {
        List<String> profiles = Arrays.asList(env.getActiveProfiles());
        if (profiles.contains("prod") || profiles.contains("test")) {
            return false;
        }
        return propEnabled || profiles.contains("dev") || profiles.contains("staging");
    }

    // ============================================================ tenants

    private void seedTenants() {
        LocalDate subUntil = LocalDate.now().plusYears(1);
        upsertAccount(ACCOUNT_A, "DEMO — Barokat Savdo", subUntil);
        upsertAccount(ACCOUNT_B, "DEMO — Raqobatchi Do'kon", subUntil);
        upsertShop(SHOP_A_MAIN, ACCOUNT_A, "Barokat Demo — Markaziy", true);
        upsertShop(SHOP_A_BRANCH, ACCOUNT_A, "Barokat Demo — Filial", false);
        upsertShop(SHOP_B_MAIN, ACCOUNT_B, "Raqobatchi Demo Do'kon", true);
    }

    private boolean accountExists(long id) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    private void upsertAccount(long id, String name, LocalDate subUntil) {
        jdbc.update(
                "INSERT INTO accounts (id, name, subscription_expires, blocked, created_at) "
                + "SELECT ?, ?, ?, FALSE, now() "
                + "WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE id = ?)",
                id, name, java.sql.Date.valueOf(subUntil), id);
    }

    private void upsertShop(long id, long accountId, String name, boolean main) {
        jdbc.update(
                "INSERT INTO shops (id, account_id, name, is_main, created_at) "
                + "SELECT ?, ?, ?, ?, now() "
                + "WHERE NOT EXISTS (SELECT 1 FROM shops WHERE id = ?)",
                id, accountId, name, main, id);
    }

    // ====================================================== account A main

    private void seedAccountAMain() {
        TenantContext.setShopId(SHOP_A_MAIN);
        try {
            Long food = category("Oziq-ovqat");
            Long household = category("Maishiy kimyo / Bolalar");

            // 20 realistic UZS products. cost / sale prices in so'm.
            // (name, barcode, categoryId, cost, sale, qty, lowStock, unit)
            P cola   = product("Coca-Cola 1.5L", "4780001000017", food, 8900, 11000, 60, 12, "dona");
            product("Pepsi 1.5L", "4780001000024", food, 8500, 10500, 48, 12, "dona");
            P non    = product("Non", "4780001000031", food, 2500, 3500, 40, 10, "dona");
            product("Sut 1L", "4780001000048", food, 9000, 12000, 3, 10, "litr"); // low-stock demo
            P oil    = product("Yog' 5L", "4780001000055", food, 78000, 95000, 20, 5, "dona");
            P sugar  = product("Shakar 1kg", "4780001000062", food, 9500, 12000, 50, 10, "kg");
            P flour  = product("Un 5kg", "4780001000079", food, 32000, 42000, 30, 6, "dona");
            product("Makaron", "4780001000086", food, 6000, 8500, 45, 10, "dona");
            product("Snickers", "4780001000093", food, 4500, 6000, 80, 20, "dona");
            product("Twix", "4780001000109", food, 4500, 6000, 75, 20, "dona");
            product("Choy 250g", "4780001000116", food, 18000, 24000, 35, 8, "dona");
            product("Qahva 100g", "4780001000123", food, 28000, 38000, 25, 6, "dona");
            product("Guruch 1kg", "4780001000130", food, 13000, 17000, 40, 10, "kg");
            product("Tuxum 10 dona", "4780001000147", food, 16000, 21000, 30, 8, "dona");
            product("Bolalar pyuresi", "4780001000154", food, 7000, 9500, 28, 8, "dona");
            P pampers4 = product("Pampers Size 4", "4780001000161", household, 95000, 120000, 18, 4, "dona");
            product("Pampers Size 5", "4780001000178", household, 98000, 125000, 16, 4, "dona");
            P napkins = product("Salfetka", "4780001000185", household, 6000, 9000, 60, 12, "dona");
            product("Idish yuvish geli", "4780001000192", household, 18000, 25000, 30, 8, "dona");
            product("Kir yuvish kukuni", "4780001000208", household, 42000, 55000, 22, 6, "dona");

            // Customers: normal, debtor, partial-payment.
            Customer normal  = customer("Doimiy mijoz", "+998901112233");
            Customer debtor  = customer("Qarzdor mijoz", "+998902223344");
            Customer partial = customer("Qisman to'lagan mijoz", "+998903334455");

            // --- POS sales (today) through the real checkout engine ---
            // Cash sale, linked to the normal customer (earns loyalty, no debt).
            checkout(PaymentType.NAQD, normal.getId(),
                    line(cola, 2), line(non, 3));
            // Card sale, walk-in.
            checkout(PaymentType.KARTA, null,
                    line(pampers4, 1), line(napkins, 2));
            // Bank-transfer sale, walk-in.
            checkout(PaymentType.TRANSFER, null, line(oil, 1));
            // On-credit (QARZGA) walk-in sale — books revenue + receivable in the
            // ledger. Per-customer debt is demonstrated via the ledgers below so
            // the figure is deterministic regardless of POS debt-linking.
            checkout(PaymentType.QARZGA, null, line(flour, 1), line(sugar, 2));

            // --- Customer debt ledgers (Mijozlar balansi) ---
            // Debtor owes 250 000 in full; partial owes 80 000 after paying 100 000.
            goods(debtor.getId(), "Oziq-ovqat (qarz)", 250_000);
            goods(partial.getId(), "Maishiy mollar (qarz)", 180_000);
            payment(partial.getId(), "Qisman to'lov", 100_000);

            // --- Expenses (today) — post to the double-entry ledger ---
            // Entered in the same scale as product prices (see expense()): the
            // ledger treats sales and expenses on one canonical scale, so the
            // demo P&L stays internally consistent (revenue 331 500, COGS
            // 261 300, gross 70 200, expenses 45 000, net 25 200).
            expense("Komunal to'lov (kunlik)", PaymentType.NAQD, 20_000);
            expense("Yetkazib berish xizmati", PaymentType.NAQD, 25_000);

            // Post everything above to the double-entry ledger synchronously, in
            // this shop's tenant scope. backfill is idempotent and excludes the
            // POS-generated payment rows, so it never double-counts the sales.
            ledgerBackfill.run();
        } finally {
            TenantContext.clear();
        }
    }

    // ==================================================== account A branch

    private void seedAccountABranch() {
        TenantContext.setShopId(SHOP_A_BRANCH);
        try {
            Long food = category("Oziq-ovqat");
            // A couple of branch products, one intentionally low on stock.
            product("Coca-Cola 1.5L", "4780001000017", food, 8900, 11000, 24, 12, "dona");
            product("Mineral suv 1L", "4780001000505", food, 3000, 4500, 4, 10, "dona"); // low-stock
            product("Pechenye", "4780001000512", food, 5000, 7000, 36, 10, "dona");
        } finally {
            TenantContext.clear();
        }
        // Stock transfer A-main -> A-branch (validated to stay inside account A).
        // Runs outside tenant scope: TransferService verifies shop ownership by
        // accountId and writes shop_id explicitly via native SQL.
        try {
            Long oilId = firstProductIdByBarcode(SHOP_A_MAIN, "4780001000055");
            if (oilId != null) {
                transfers.create(ACCOUNT_A, "demo-seed", new TransferRequest(
                        SHOP_A_MAIN, SHOP_A_BRANCH, oilId, new BigDecimal("5"),
                        "Demo: markazdan filialga ko'chirish"));
            }
        } catch (RuntimeException ex) {
            log.warn("Demo transfer skipped: {}", ex.toString());
        }
    }

    // ========================================================= account B

    private void seedAccountB() {
        TenantContext.setShopId(SHOP_B_MAIN);
        try {
            Long food = category("Raqobatchi mahsulotlari");
            // Deliberately DISTINCT products so cross-tenant leakage is obvious.
            P water = product("Humo suv 0.5L", "4790002000011", food, 2000, 3500, 100, 20, "dona");
            product("Energiya ichimligi", "4790002000028", food, 9000, 14000, 40, 10, "dona");
            product("Choy paqovkali", "4790002000035", food, 12000, 18000, 30, 8, "dona");
            product("Shokolad plitka", "4790002000042", food, 7000, 11000, 50, 12, "dona");
            product("Konfet 1kg", "4790002000059", food, 22000, 30000, 25, 6, "kg");

            Customer cust = customer("Raqobatchi mijozi", "+998935556677");
            checkout(PaymentType.NAQD, cust.getId(), line(water, 4));
            expense("Raqobatchi ijarasi (kunlik)", PaymentType.NAQD, 30_000);

            ledgerBackfill.run();
        } finally {
            TenantContext.clear();
        }
    }

    // ============================================================ helpers

    /** Lightweight holder so checkout lines can reference a created product. */
    private record P(Long id) { }

    private Long category(String name) {
        Category c = new Category();
        c.setName(name);
        return categories.save(c).getId();
    }

    private P product(String name, String barcode, Long categoryId,
                      long cost, long sale, int qty, int lowStock, String unit) {
        Product p = new Product();
        p.setName(name);
        p.setBarcode(barcode);
        p.setCategoryId(categoryId);
        p.setPurchasePrice(BigDecimal.valueOf(cost));
        p.setSalePrice(BigDecimal.valueOf(sale));
        p.setQuantity(qty);
        p.setLowStockThreshold(lowStock);
        p.setUnit(unit);
        return new P(products.save(p).getId());
    }

    private Customer customer(String name, String phone) {
        Customer c = new Customer();
        c.setName(name);
        c.setPhone(phone);
        return customers.save(c);
    }

    private CartItem line(P product, int qty) {
        return new CartItem(product.id(), qty, BigDecimal.ZERO, null);
    }

    private void checkout(PaymentType method, Long customerId, CartItem... items) {
        CheckoutRequest req = new CheckoutRequest(
                List.of(items), BigDecimal.ZERO, BigDecimal.ZERO,
                method.name(), customerId, "Demo savdo", null);
        pos.checkout(req, "demo-kassir");
    }

    private void goods(Long customerId, String description, long amount) {
        ledger(customerId, CustomerTxType.GOODS, description, amount);
    }

    private void payment(Long customerId, String description, long amount) {
        ledger(customerId, CustomerTxType.PAYMENT, description, amount);
    }

    private void ledger(Long customerId, CustomerTxType type, String description, long amount) {
        CustomerTransaction tx = new CustomerTransaction();
        tx.setCustomerId(customerId);
        tx.setDate(LocalDate.now());
        tx.setType(type);
        tx.setDescription(description);
        tx.setAmount(BigDecimal.valueOf(amount));
        customerTx.save(tx);
    }

    private void expense(String name, PaymentType method, long amount) {
        // Currency.USD = "do not rescale": the ledger posts sale revenue/COGS
        // unconverted, so expenses are entered on the same canonical scale to
        // keep the P&L self-consistent. A genuine multi-currency normalisation
        // across the ledger is tracked as a P1 follow-up (see ACCOUNTING_RULES.md).
        expenses.create(new ExpenseRequest(
                LocalDate.now(), name, BigDecimal.valueOf(amount), method,
                null, null, null, Currency.USD, "Demo xarajat"));
    }

    /** Native lookup (no tenant filter) of a product id by shop + barcode. */
    private Long firstProductIdByBarcode(long shopId, String barcode) {
        List<Long> ids = jdbc.query(
                "SELECT id FROM products WHERE shop_id = ? AND barcode = ? ORDER BY id LIMIT 1",
                (rs, n) -> rs.getLong(1), shopId, barcode);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
