package uz.barakat.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.domain.Currency;
import uz.barakat.market.domain.Sale;
import uz.barakat.market.domain.SaleItem;
import uz.barakat.market.domain.Product;
import uz.barakat.market.dto.AccountingDtos.BalanceSheetResponse;
import uz.barakat.market.dto.AccountingDtos.ClosePeriodRequest;
import uz.barakat.market.dto.AccountingDtos.JournalEntryRequest;
import uz.barakat.market.dto.AccountingDtos.JournalLineRequest;
import uz.barakat.market.dto.AccountingDtos.ProfitLossResponse;
import uz.barakat.market.dto.AccountingDtos.TrialBalanceResponse;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SaleRepository;
import uz.barakat.market.repository.ShopRepository;

/**
 * End-to-end check of the double-entry core against H2: a posted sale yields a
 * balanced ledger that reconciles to the P&L and balance sheet, manual entries
 * must balance, and a closed period rejects new postings.
 */
@SpringBootTest
@ActiveProfiles("test")
class AccountingFlowTest {

    @Autowired private LedgerPostingService posting;
    @Autowired private FinancialStatementService statements;
    @Autowired private JournalService journal;
    @Autowired private AccountingPeriodService periods;
    @Autowired private ChartOfAccountsService chart;
    @Autowired private ProductRepository products;
    @Autowired private SaleRepository sales;
    @Autowired private ShopRepository shops;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    /**
     * One sequential flow on a fresh in-memory DB so the exact-number
     * assertions are deterministic: the reconciliation checks run BEFORE any
     * extra (contaminating) entries are added later in the method.
     */
    @Test
    void accountingCoreEndToEnd() {
        Long shopId = shops.findAll().stream().findFirst().orElseThrow().getId();
        TenantContext.setShopId(shopId);

        Product p = new Product();
        p.setName("Ledger widget");
        p.setPurchasePrice(new BigDecimal("10"));   // cost (USD)
        p.setSalePrice(new BigDecimal("15"));        // sale price (USD)
        p.setQuantity(100);
        p = products.save(p);

        // A cash sale of 2 units: subtotal 30, no discount, COGS 20.
        Sale sale = new Sale();
        sale.setPaymentMethod("NAQD");
        sale.setSubtotalUzs(new BigDecimal("30"));
        sale.setTotalUzs(new BigDecimal("30"));
        SaleItem item = new SaleItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(2);
        item.setUnitPriceUzs(new BigDecimal("15"));
        item.setLineTotalUzs(new BigDecimal("30"));
        sale.addItem(item);
        sale = sales.save(sale);

        posting.postSale(sale);

        LocalDate from = LocalDate.now().withDayOfMonth(1);
        LocalDate to = LocalDate.now();

        // --- Reconciliation (only the sale exists at this point) ---
        // Trial balance: Dr cash 30 + COGS 20 = Cr sales 30 + inventory 20.
        TrialBalanceResponse tb = statements.trialBalance(from, to);
        assertThat(tb.balanced()).isTrue();
        assertThat(tb.totalDebit()).isEqualByComparingTo(tb.totalCredit());
        assertThat(tb.totalDebit()).isEqualByComparingTo("50.00");

        // P&L: revenue 30, COGS 20, gross/net 10.
        ProfitLossResponse pnl = statements.profitLoss(from, to);
        assertThat(pnl.revenueTotal()).isEqualByComparingTo("30");
        assertThat(pnl.cogsTotal()).isEqualByComparingTo("20");
        assertThat(pnl.grossProfit()).isEqualByComparingTo("10");
        assertThat(pnl.netProfit()).isEqualByComparingTo("10");

        // Balance sheet balances (Assets = Liabilities + Equity + net income).
        assertThat(statements.balanceSheet(to).balanced()).isTrue();

        // Idempotent: re-posting the same sale adds no second entry.
        posting.postSale(sale);
        assertThat(statements.trialBalance(from, to).totalDebit()).isEqualByComparingTo("50.00");

        // --- Manual entry must balance ---
        Long cash = chart.accountByCode(ChartOfAccountsService.CASH).getId();
        Long income = chart.accountByCode(ChartOfAccountsService.OTHER_INCOME).getId();
        JournalEntryRequest bad = new JournalEntryRequest(to, "bad", List.of(
                new JournalLineRequest(cash, new BigDecimal("5"), null, Currency.USD, null),
                new JournalLineRequest(income, null, new BigDecimal("9"), Currency.USD, null)));
        assertThatThrownBy(() -> journal.create(bad)).isInstanceOf(BadRequestException.class);

        JournalEntryRequest ok = new JournalEntryRequest(to, "ok", List.of(
                new JournalLineRequest(cash, new BigDecimal("5"), null, Currency.USD, null),
                new JournalLineRequest(income, null, new BigDecimal("5"), Currency.USD, null)));
        assertThat(journal.create(ok).totalDebit()).isEqualByComparingTo("5");

        // --- Closed period rejects a posting dated inside it ---
        periods.close(new ClosePeriodRequest(
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31), "closed"));
        JournalEntryRequest inClosed = new JournalEntryRequest(
                LocalDate.of(2020, 1, 15), "into closed period", List.of(
                new JournalLineRequest(cash, new BigDecimal("5"), null, Currency.USD, null),
                new JournalLineRequest(income, null, new BigDecimal("5"), Currency.USD, null)));
        assertThatThrownBy(() -> journal.create(inClosed)).isInstanceOf(BadRequestException.class);
        periods.assertOpen(to);   // today is still open
    }
}
