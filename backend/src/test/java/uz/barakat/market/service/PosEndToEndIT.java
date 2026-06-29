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
import uz.barakat.market.domain.CustomerTransaction;
import uz.barakat.market.domain.CustomerTxType;
import uz.barakat.market.domain.Payment;
import uz.barakat.market.domain.PaymentDirection;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.Sale;
import uz.barakat.market.domain.Shop;
import uz.barakat.market.dto.AccountingDtos.ProfitLossResponse;
import uz.barakat.market.dto.PosDtos.CartItem;
import uz.barakat.market.dto.PosDtos.CheckoutRequest;
import uz.barakat.market.dto.PosDtos.RefundItemRequest;
import uz.barakat.market.dto.PosDtos.RefundRequest;
import uz.barakat.market.dto.PosDtos.SaleResponse;
import uz.barakat.market.repository.CustomerRepository;
import uz.barakat.market.repository.CustomerTransactionRepository;
import uz.barakat.market.repository.PaymentRepository;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SaleRepository;
import uz.barakat.market.repository.ShopRepository;
import uz.barakat.market.domain.Customer;

/**
 * End-to-end POS business flow against H2, asserting real DB/business state —
 * not UI. Each test runs in its own freshly-created shop so the numbers are
 * deterministic, and on an isolated in-memory DB so the existing exact-number
 * accounting tests are never perturbed.
 *
 * Covers: total math, stock decrement, payment journal per method, debt sale →
 * customer debt, repayment → debt down, refund → stock back + counter-payment,
 * and (the V37 fix) COGS booked from the cost frozen at sale time.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:pos_e2e_it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "app.demo-seed.enabled=false"
})
class PosEndToEndIT {

    @Autowired private PosService pos;
    @Autowired private ProductRepository products;
    @Autowired private CustomerRepository customers;
    @Autowired private CustomerTransactionRepository customerTx;
    @Autowired private PaymentRepository payments;
    @Autowired private SaleRepository sales;
    @Autowired private ShopRepository shops;
    @Autowired private LedgerBackfillService backfill;
    @Autowired private FinancialStatementService statements;
    @Autowired private JdbcTemplate jdbc;

    private Long shopId;

    @BeforeEach
    void newShop() {
        Shop s = new Shop();
        s.setAccountId(1L);
        s.setName("POS E2E shop");
        shopId = shops.save(s).getId();
        TenantContext.setShopId(shopId);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    // ---- cash sale: total math, stock decrement, payment journal, revenue ----

    @Test
    void cashSaleDecrementsStockBooksPaymentAndRevenue() {
        Product p = product("Cola", 8_000, 11_000, 50);

        // 2 units, 1 000 line discount => 2*11000 - 1000 = 21 000
        SaleResponse sale = checkout("NAQD", null,
                new CartItem(p.getId(), 2, new BigDecimal("1000"), null));

        assertThat(sale.totalUzs()).isEqualByComparingTo("21000");
        // Stock fell by exactly the sold quantity.
        assertThat(products.findById(p.getId()).orElseThrow().getQuantity()).isEqualTo(48);
        // A single INCOMING cash payment was booked for the net total.
        Payment pay = payments.findById(sale.paymentId()).orElseThrow();
        assertThat(pay.getDirection()).isEqualTo(PaymentDirection.INCOMING);
        assertThat(pay.getAmount()).isEqualByComparingTo("21000");
        // Sale shows in history.
        assertThat(sales.findById(sale.id())).isPresent();

        // Accounting: net revenue = 22 000 gross - 1 000 discount (contra-revenue),
        // COGS = 2 * 8 000 = 16 000, gross profit = 5 000.
        backfill.run();
        ProfitLossResponse pnl = pnl();
        assertThat(pnl.revenueTotal()).isEqualByComparingTo("21000");
        assertThat(pnl.cogsTotal()).isEqualByComparingTo("16000");
        assertThat(pnl.grossProfit()).isEqualByComparingTo("5000");
    }

    // ---- COGS is frozen at sale time, immune to later cost edits (V37) ----

    @Test
    void cogsUsesCostSnapshotNotLaterEditedProductCost() {
        Product p = product("Widget", 100, 150, 100);
        SaleResponse sale = checkout("NAQD", null, new CartItem(p.getId(), 2, BigDecimal.ZERO, null));

        // The line froze the cost at 100.
        Sale persisted = sales.findById(sale.id()).orElseThrow();
        assertThat(persisted.getItems().get(0).getCostAtSaleUzs()).isEqualByComparingTo("100");

        // Someone re-prices the product's cost AFTER the sale.
        Product reload = products.findById(p.getId()).orElseThrow();
        reload.setPurchasePrice(new BigDecimal("999"));
        products.save(reload);

        // COGS must stay 2*100 = 200 (snapshot), NOT 2*999.
        backfill.run();
        assertThat(pnl().cogsTotal()).isEqualByComparingTo("200");
    }

    // ---- payment methods are recorded distinctly; QARZGA books no payment ----

    @Test
    void paymentMethodsAreSeparated() {
        Product p = product("Item", 1_000, 2_000, 100);

        SaleResponse cash = checkout("NAQD", null, new CartItem(p.getId(), 1, BigDecimal.ZERO, null));
        SaleResponse card = checkout("KARTA", null, new CartItem(p.getId(), 1, BigDecimal.ZERO, null));
        SaleResponse wire = checkout("TRANSFER", null, new CartItem(p.getId(), 1, BigDecimal.ZERO, null));
        Customer c = customer("Qarzdor");
        SaleResponse credit = checkout("QARZGA", c.getId(), new CartItem(p.getId(), 1, BigDecimal.ZERO, null));

        assertThat(payments.findById(cash.paymentId()).orElseThrow().getMethod().name()).isEqualTo("NAQD");
        assertThat(payments.findById(card.paymentId()).orElseThrow().getMethod().name()).isEqualTo("KARTA");
        assertThat(payments.findById(wire.paymentId()).orElseThrow().getMethod().name()).isEqualTo("TRANSFER");
        // On-credit sale books NO payment row (it's a receivable, not cash in).
        assertThat(credit.paymentId()).isNull();
    }

    // ---- debt sale raises customer debt; a repayment lowers it ----

    @Test
    void debtSaleRaisesCustomerDebtAndRepaymentLowersIt() {
        Product p = product("Telefon", 1_000_000, 1_500_000, 10);
        Customer c = customer("Qarzdor mijoz");

        checkout("QARZGA", c.getId(), new CartItem(p.getId(), 1, BigDecimal.ZERO, null));
        assertThat(balanceOf(c.getId())).isEqualByComparingTo("1500000"); // owes the full sale

        // Customer pays back 600 000.
        CustomerTransaction pay = new CustomerTransaction();
        pay.setCustomerId(c.getId());
        pay.setDate(LocalDate.now());
        pay.setType(CustomerTxType.PAYMENT);
        pay.setAmount(new BigDecimal("600000"));
        pay.setDescription("Qarz to'lovi");
        customerTx.save(pay);

        assertThat(balanceOf(c.getId())).isEqualByComparingTo("900000"); // debt went down
    }

    // ---- refund restores stock and books an OUTGOING counter-payment ----

    @Test
    void refundRestoresStockAndBooksCounterPayment() {
        Product p = product("Mahsulot", 4_000, 6_000, 20);
        SaleResponse sale = checkout("NAQD", null, new CartItem(p.getId(), 5, BigDecimal.ZERO, null));
        assertThat(products.findById(p.getId()).orElseThrow().getQuantity()).isEqualTo(15);

        Long itemId = sale.items().get(0).id();
        SaleResponse refunded = pos.refund(sale.id(),
                new RefundRequest(List.of(new RefundItemRequest(itemId, 2)), "qaytdi"));

        // 2 units back in stock.
        assertThat(products.findById(p.getId()).orElseThrow().getQuantity()).isEqualTo(17);
        assertThat(refunded.refundedTotalUzs()).isEqualByComparingTo("12000"); // 2 * 6000
        // An OUTGOING payment (cash back) was booked for this shop. Queried via
        // JDBC scoped to the shop — payments.findAll() would load other tests'
        // rows and trip the cross-tenant @PostLoad guard.
        Integer cashBacks = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE shop_id = ? AND direction = 'OUTGOING' "
                + "AND amount = 12000", Integer.class, shopId);
        assertThat(cashBacks).isEqualTo(1);
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

    private Customer customer(String name) {
        Customer c = new Customer();
        c.setName(name);
        return customers.save(c);
    }

    private SaleResponse checkout(String method, Long customerId, CartItem item) {
        return pos.checkout(new CheckoutRequest(
                List.of(item), BigDecimal.ZERO, BigDecimal.ZERO, method, customerId, null, null),
                "tester");
    }

    private ProfitLossResponse pnl() {
        return statements.profitLoss(LocalDate.now().withDayOfMonth(1), LocalDate.now());
    }

    private BigDecimal balanceOf(Long customerId) {
        BigDecimal bal = BigDecimal.ZERO;
        for (CustomerTransaction t : customerTx.findByCustomerIdOrderByDateDescIdDesc(customerId)) {
            bal = t.getType() == CustomerTxType.GOODS ? bal.add(t.getAmount()) : bal.subtract(t.getAmount());
        }
        return bal;
    }
}
