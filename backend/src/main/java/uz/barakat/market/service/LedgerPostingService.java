package uz.barakat.market.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Currency;
import uz.barakat.market.domain.Expense;
import uz.barakat.market.domain.GlAccount;
import uz.barakat.market.domain.HomeExpense;
import uz.barakat.market.domain.JournalEntry;
import uz.barakat.market.domain.JournalLine;
import uz.barakat.market.domain.JournalSource;
import uz.barakat.market.domain.ManagementCost;
import uz.barakat.market.domain.ManagementCostType;
import uz.barakat.market.domain.Payment;
import uz.barakat.market.domain.PaymentCategory;
import uz.barakat.market.domain.PaymentDirection;
import uz.barakat.market.domain.PaymentType;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.Sale;
import uz.barakat.market.domain.SaleItem;
import uz.barakat.market.domain.StockMovement;
import uz.barakat.market.domain.StockReason;
import uz.barakat.market.repository.ExpenseRepository;
import uz.barakat.market.repository.HomeExpenseRepository;
import uz.barakat.market.repository.JournalEntryRepository;
import uz.barakat.market.repository.ManagementCostRepository;
import uz.barakat.market.repository.PaymentRepository;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SaleRepository;
import uz.barakat.market.repository.StockMovementRepository;
import uz.barakat.market.service.LedgerEvents.RefundLine;
import uz.barakat.market.service.LedgerEvents.SaleRefunded;

import static uz.barakat.market.service.ChartOfAccountsService.BANK;
import static uz.barakat.market.service.ChartOfAccountsService.CASH;
import static uz.barakat.market.service.ChartOfAccountsService.COGS;
import static uz.barakat.market.service.ChartOfAccountsService.INVENTORY;
import static uz.barakat.market.service.ChartOfAccountsService.OPENING_EQUITY;
import static uz.barakat.market.service.ChartOfAccountsService.OTHER_EXPENSE;
import static uz.barakat.market.service.ChartOfAccountsService.OTHER_INCOME;
import static uz.barakat.market.service.ChartOfAccountsService.PAYABLE;
import static uz.barakat.market.service.ChartOfAccountsService.RECEIVABLE;
import static uz.barakat.market.service.ChartOfAccountsService.SALARY;
import static uz.barakat.market.service.ChartOfAccountsService.SALES;
import static uz.barakat.market.service.ChartOfAccountsService.SALES_DISCOUNT;
import static uz.barakat.market.service.ChartOfAccountsService.SALES_RETURNS;
import static uz.barakat.market.service.ChartOfAccountsService.SHRINKAGE;
import static uz.barakat.market.service.ChartOfAccountsService.TAX;

/**
 * Turns operational events into balanced double-entry journal entries, in the
 * canonical USD. Every method is idempotent (guarded by source + source_ref)
 * and silently skips a closed period, so it is safe to call from the
 * after-commit listener AND from the backfill replay.
 *
 * <p>Sign convention: revenue + cash/receivable come from the {@link Sale};
 * inventory in/out for purchases & write-offs come from {@link StockMovement};
 * COGS is posted on the sale itself. POS SALE/RETURN movements never reach
 * {@link #postStockMovement} (they are created in PosService, not
 * ProductService.logMovement), so inventory is counted exactly once.
 */
@Service
@Transactional
public class LedgerPostingService {

    private static final Logger log = LoggerFactory.getLogger(LedgerPostingService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal EPS = new BigDecimal("0.05");

    private final JournalEntryRepository entries;
    private final ChartOfAccountsService chart;
    private final ProductRepository products;
    private final MoneyConverter converter;
    private final AccountingPeriodService periods;
    private final SaleRepository sales;
    private final ExpenseRepository expenses;
    private final HomeExpenseRepository homeExpenses;
    private final ManagementCostRepository mgmtCosts;
    private final PaymentRepository payments;
    private final StockMovementRepository movements;

    public LedgerPostingService(JournalEntryRepository entries, ChartOfAccountsService chart,
                                ProductRepository products, MoneyConverter converter,
                                AccountingPeriodService periods, SaleRepository sales,
                                ExpenseRepository expenses, HomeExpenseRepository homeExpenses,
                                ManagementCostRepository mgmtCosts, PaymentRepository payments,
                                StockMovementRepository movements) {
        this.entries = entries;
        this.chart = chart;
        this.products = products;
        this.converter = converter;
        this.periods = periods;
        this.sales = sales;
        this.expenses = expenses;
        this.homeExpenses = homeExpenses;
        this.mgmtCosts = mgmtCosts;
        this.payments = payments;
        this.movements = movements;
    }

    // ==================================================== load + post (events)
    //
    // Called by LedgerPostingListener AFTER the source transaction commits.
    // Each runs in its own transaction (no tx is active in the AFTER_COMMIT
    // phase, so the class-level @Transactional starts a fresh one) with the
    // tenant filter enabled by TenantFilterAspect — the row is loaded scoped to
    // the same shop, then posted. The listener wraps these in try/catch so a
    // ledger failure never escapes to the user.

    public void handleSale(Long saleId) {
        sales.findById(saleId).ifPresent(this::postSale);
    }

    public void handleSaleRefund(SaleRefunded ev) {
        postSaleRefund(ev.shopId(), ev.date(), ev.paymentMethod(),
                ev.refundedAmountUsd(), ev.returnedLines(), ev.uniqueRef());
    }

    public void handleExpense(Long id) {
        expenses.findById(id).ifPresent(this::postExpense);
    }

    public void handleHomeExpense(Long id) {
        homeExpenses.findById(id).ifPresent(this::postHomeExpense);
    }

    public void handleManagementCost(Long id) {
        mgmtCosts.findById(id).ifPresent(this::postManagementCost);
    }

    public void handlePayment(Long id) {
        payments.findById(id).ifPresent(this::postPayment);
    }

    public void handleStockMovement(Long id) {
        movements.findById(id).ifPresent(this::postStockMovement);
    }

    // ================================================================= sale

    /** Books a POS sale: revenue (gross) + cash/receivable + discount + COGS. */
    public void postSale(Sale sale) {
        if (sale == null) {
            return;
        }
        String ref = String.valueOf(sale.getId());
        LocalDate date = sale.getCreatedAt() != null
                ? sale.getCreatedAt().toLocalDate() : LocalDate.now();
        if (alreadyPosted(JournalSource.SALE, ref) || periods.isLocked(date)) {
            return;
        }
        Map<String, GlAccount> acc = chart.byCode();

        BigDecimal subtotal = nz(sale.getSubtotalUzs());   // gross, USD-valued
        BigDecimal total = nz(sale.getTotalUzs());         // net after discounts
        BigDecimal discount = subtotal.subtract(total).max(ZERO);
        BigDecimal cogs = ZERO;
        for (SaleItem it : sale.getItems()) {
            cogs = cogs.add(unitCost(it.getProductId())
                    .multiply(BigDecimal.valueOf(it.getQuantity())));
        }

        JournalEntry e = newEntry(sale.getShopId(), date, JournalSource.SALE, ref,
                "Sotuv #" + sale.getId());
        debit(e, acc, cashAccountFor(sale.getPaymentMethod()), total, "Tushum");
        debit(e, acc, SALES_DISCOUNT, discount, "Chegirma");
        credit(e, acc, SALES, subtotal, "Savdo tushumi");
        debit(e, acc, COGS, cogs, "Tannarx");
        credit(e, acc, INVENTORY, cogs, "Zaxiradan chiqdi");
        save(e);
    }

    /** Books a POS refund: sales-return contra + cash back + COGS/inventory reversal. */
    public void postSaleRefund(Long shopId, LocalDate date, String paymentMethod,
                               BigDecimal refundUsd, List<RefundLine> returned, String uniqueRef) {
        LocalDate d = date != null ? date : LocalDate.now();
        if (alreadyPosted(JournalSource.SALE_REFUND, uniqueRef) || periods.isLocked(d)) {
            return;
        }
        Map<String, GlAccount> acc = chart.byCode();
        BigDecimal returnedCost = ZERO;
        if (returned != null) {
            for (RefundLine r : returned) {
                returnedCost = returnedCost.add(unitCost(r.productId())
                        .multiply(BigDecimal.valueOf(r.quantity())));
            }
        }
        JournalEntry e = newEntry(shopId, d, JournalSource.SALE_REFUND, uniqueRef, "Qaytarish");
        debit(e, acc, SALES_RETURNS, nz(refundUsd), "Qaytarilgan savdo");
        credit(e, acc, cashAccountFor(paymentMethod), nz(refundUsd), "Pul qaytdi");
        debit(e, acc, INVENTORY, returnedCost, "Zaxiraga qaytdi");
        credit(e, acc, COGS, returnedCost, "Tannarx qaytdi");
        save(e);
    }

    // ============================================================= expenses

    public void postExpense(Expense x) {
        if (x == null) {
            return;
        }
        postExpenseLike(JournalSource.EXPENSE, String.valueOf(x.getId()), x.getShopId(),
                x.getDate(), "Xarajat: " + x.getName(), x.getCurrency(),
                x.getCashAmount(), x.getNaqdAmount(), x.getCardAmount(), x.getAmount());
    }

    public void postHomeExpense(HomeExpense x) {
        if (x == null) {
            return;
        }
        postExpenseLike(JournalSource.HOME_EXPENSE, String.valueOf(x.getId()), x.getShopId(),
                x.getDate(), "Do'kon xarajati: " + x.getName(), x.getCurrency(),
                x.getCashAmount(), x.getNaqdAmount(), x.getCardAmount(), x.getAmount());
    }

    private void postExpenseLike(JournalSource source, String ref, Long shopId, LocalDate date,
                                 String memo, Currency cur, BigDecimal cashAmt, BigDecimal naqdAmt,
                                 BigDecimal cardAmt, BigDecimal amount) {
        LocalDate d = date != null ? date : LocalDate.now();
        if (alreadyPosted(source, ref) || periods.isLocked(d)) {
            return;
        }
        Map<String, GlAccount> acc = chart.byCode();
        BigDecimal cashUsd = converter.toUsd(nz(cashAmt).add(nz(naqdAmt)), cur);
        BigDecimal cardUsd = converter.toUsd(nz(cardAmt), cur);
        BigDecimal paidUsd = cashUsd.add(cardUsd);

        JournalEntry e = newEntry(shopId, d, source, ref, memo);
        if (paidUsd.signum() <= 0) {
            // QARZGA (on credit): owe a supplier instead of paying cash.
            BigDecimal amtUsd = converter.toUsd(nz(amount), cur);
            debit(e, acc, OTHER_EXPENSE, amtUsd, "Xarajat");
            credit(e, acc, PAYABLE, amtUsd, "Qarzga");
        } else {
            debit(e, acc, OTHER_EXPENSE, paidUsd, "Xarajat");
            credit(e, acc, CASH, cashUsd, "Naqd");
            credit(e, acc, BANK, cardUsd, "Karta");
        }
        save(e);
    }

    public void postManagementCost(ManagementCost c) {
        if (c == null) {
            return;
        }
        String ref = String.valueOf(c.getId());
        LocalDate d = c.getDate() != null ? c.getDate() : LocalDate.now();
        if (alreadyPosted(JournalSource.MANAGEMENT_COST, ref) || periods.isLocked(d)) {
            return;
        }
        Map<String, GlAccount> acc = chart.byCode();
        BigDecimal amtUsd = converter.toUsd(nz(c.getAmount()), c.getCurrency());
        String expCode = c.getType() == ManagementCostType.SALARY ? SALARY
                : c.getType() == ManagementCostType.TAX ? TAX : OTHER_EXPENSE;
        JournalEntry e = newEntry(c.getShopId(), d, JournalSource.MANAGEMENT_COST, ref,
                c.getName());
        debit(e, acc, expCode, amtUsd, "Xarajat");
        credit(e, acc, CASH, amtUsd, "Naqd");
        save(e);
    }

    // ============================================================== payment

    /** Manual payment-journal row (supplier payment, debt collection, salary...). */
    public void postPayment(Payment p) {
        if (p == null) {
            return;
        }
        String ref = String.valueOf(p.getId());
        LocalDate d = p.getDate() != null ? p.getDate() : LocalDate.now();
        if (alreadyPosted(JournalSource.PAYMENT, ref) || periods.isLocked(d)) {
            return;
        }
        Map<String, GlAccount> acc = chart.byCode();
        BigDecimal amtUsd = converter.toUsd(nz(p.getAmount()), p.getCurrency());
        String cash = cashAccountFor(p.getMethod() == null ? null : p.getMethod().name());

        JournalEntry e = newEntry(p.getShopId(), d, JournalSource.PAYMENT, ref,
                p.getParty() != null ? p.getParty() : "To'lov");
        if (p.getDirection() == PaymentDirection.INCOMING) {
            String counter = p.getCategory() == PaymentCategory.CUSTOMER ? RECEIVABLE : OTHER_INCOME;
            debit(e, acc, cash, amtUsd, "Kirim");
            credit(e, acc, counter, amtUsd, label(p.getCategory()));
        } else {
            String counter = switch (p.getCategory()) {
                case SUPPLIER -> PAYABLE;
                case SALARY -> SALARY;
                case TAX -> TAX;
                default -> OTHER_EXPENSE;
            };
            debit(e, acc, counter, amtUsd, label(p.getCategory()));
            credit(e, acc, cash, amtUsd, "Chiqim");
        }
        save(e);
    }

    // ======================================================= stock movement

    /** Inventory in/out for warehouse intake, correction and write-off (NOT sales). */
    public void postStockMovement(StockMovement m) {
        if (m == null || m.getReason() == StockReason.SALE || m.getReason() == StockReason.RETURN) {
            return;   // sales handled by postSale / postSaleRefund
        }
        boolean in = m.getDelta() > 0;
        JournalSource source = in ? JournalSource.STOCK_IN : JournalSource.STOCK_WRITEOFF;
        String ref = String.valueOf(m.getId());
        LocalDate d = m.getCreatedAt() != null ? m.getCreatedAt().toLocalDate() : LocalDate.now();
        if (alreadyPosted(source, ref) || periods.isLocked(d)) {
            return;
        }
        BigDecimal cost = m.getUnitCostPrice() != null ? m.getUnitCostPrice()
                : unitCost(m.getProductId());
        BigDecimal amtUsd = cost.multiply(BigDecimal.valueOf(Math.abs(m.getDelta())));
        if (amtUsd.signum() <= 0) {
            return;
        }
        Map<String, GlAccount> acc = chart.byCode();
        JournalEntry e = newEntry(m.getShopId(), d, source, ref, stockMemo(m));
        if (in) {
            // Where the offsetting credit lands depends on why stock grew.
            String counter = switch (m.getReason()) {
                case DELIVERY -> PAYABLE;        // bought from a supplier on account
                case INITIAL -> OPENING_EQUITY;  // opening stock = owner-contributed
                default -> SHRINKAGE;            // positive correction = recovery
            };
            debit(e, acc, INVENTORY, amtUsd, "Zaxiraga kirdi");
            credit(e, acc, counter, amtUsd, reasonLabel(m.getReason()));
        } else {
            debit(e, acc, SHRINKAGE, amtUsd, reasonLabel(m.getReason()));
            credit(e, acc, INVENTORY, amtUsd, "Zaxiradan chiqdi");
        }
        save(e);
    }

    // =============================================================== helpers

    private boolean alreadyPosted(JournalSource source, String ref) {
        return entries.existsBySourceAndSourceRef(source, ref);
    }

    private JournalEntry newEntry(Long shopId, LocalDate date, JournalSource source,
                                  String ref, String memo) {
        JournalEntry e = new JournalEntry();
        e.setShopId(shopId);
        e.setEntryDate(date);
        e.setSource(source);
        e.setSourceRef(ref);
        e.setMemo(memo);
        e.setPosted(true);
        e.setCreatedBy("auto");
        return e;
    }

    private void debit(JournalEntry e, Map<String, GlAccount> acc, String code,
                       BigDecimal amtUsd, String memo) {
        if (amtUsd == null || amtUsd.signum() <= 0) {
            return;
        }
        line(e, acc, code, amtUsd, ZERO, memo);
    }

    private void credit(JournalEntry e, Map<String, GlAccount> acc, String code,
                        BigDecimal amtUsd, String memo) {
        if (amtUsd == null || amtUsd.signum() <= 0) {
            return;
        }
        line(e, acc, code, ZERO, amtUsd, memo);
    }

    private void line(JournalEntry e, Map<String, GlAccount> acc, String code,
                      BigDecimal debit, BigDecimal credit, String memo) {
        GlAccount a = acc.get(code);
        if (a == null) {
            throw new IllegalStateException("Hisoblar rejasida hisob yo'q: " + code);
        }
        JournalLine l = new JournalLine();
        l.setShopId(e.getShopId());
        l.setAccountId(a.getId());
        l.setDebit(debit);
        l.setCredit(credit);
        l.setCurrency(Currency.USD);
        l.setOrigAmount(debit.signum() > 0 ? debit : credit);
        l.setMemo(memo);
        e.addLine(l);
    }

    /** Validates the entry balances and has real lines, then saves it. */
    private void save(JournalEntry e) {
        if (e.getLines().isEmpty()) {
            return;   // nothing economically meaningful (e.g. a $0 sale)
        }
        BigDecimal debit = ZERO;
        BigDecimal credit = ZERO;
        for (JournalLine l : e.getLines()) {
            debit = debit.add(nz(l.getDebit()));
            credit = credit.add(nz(l.getCredit()));
        }
        if (debit.subtract(credit).abs().compareTo(EPS) > 0) {
            log.error("Refusing unbalanced auto-entry {} ref={}: debit={} credit={}",
                    e.getSource(), e.getSourceRef(), debit, credit);
            return;
        }
        entries.save(e);
    }

    private BigDecimal unitCost(Long productId) {
        if (productId == null) {
            return ZERO;
        }
        return products.findById(productId).map(Product::getPurchasePrice).orElse(ZERO);
    }

    private static String cashAccountFor(String method) {
        PaymentType m;
        try {
            m = method == null ? PaymentType.NAQD : PaymentType.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException ex) {
            m = PaymentType.NAQD;
        }
        return switch (m) {
            case KARTA, P2P, TRANSFER -> BANK;
            case QARZGA -> RECEIVABLE;
            default -> CASH;   // NAQD, KASSA, ARALASH
        };
    }

    private static String label(PaymentCategory c) {
        return switch (c) {
            case CUSTOMER -> "Mijoz";
            case SUPPLIER -> "Yetkazib beruvchi";
            case SALARY -> "Ish haqi";
            case TAX -> "Soliq";
            default -> "Boshqa";
        };
    }

    private static String reasonLabel(StockReason r) {
        return switch (r) {
            case DELIVERY -> "Yetkazib berish";
            case INITIAL -> "Boshlang'ich qoldiq";
            case CORRECTION -> "Tuzatish";
            case WRITEOFF -> "Hisobdan chiqarish";
            default -> r.name();
        };
    }

    private static String stockMemo(StockMovement m) {
        return reasonLabel(m.getReason()) + (m.getNote() != null ? " — " + m.getNote() : "");
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }
}
