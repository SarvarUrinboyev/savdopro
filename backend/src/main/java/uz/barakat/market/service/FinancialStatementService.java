package uz.barakat.market.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.domain.GlAccount;
import uz.barakat.market.domain.GlAccountType;
import uz.barakat.market.domain.JournalEntry;
import uz.barakat.market.domain.JournalLine;
import uz.barakat.market.domain.JournalSource;
import uz.barakat.market.dto.AccountingDtos.BalanceSheetResponse;
import uz.barakat.market.dto.AccountingDtos.CashFlowLine;
import uz.barakat.market.dto.AccountingDtos.CashFlowResponse;
import uz.barakat.market.dto.AccountingDtos.ProfitLossResponse;
import uz.barakat.market.dto.AccountingDtos.StatementLine;
import uz.barakat.market.dto.AccountingDtos.TrialBalanceResponse;
import uz.barakat.market.dto.AccountingDtos.TrialBalanceRow;
import uz.barakat.market.repository.JournalEntryRepository;
import uz.barakat.market.repository.JournalLineRepository;

/**
 * Derives the financial statements from the ledger — all in canonical USD.
 * Because every posting balances, the trial balance and balance sheet balance
 * by construction; P&L = revenue − COGS − expenses reconciles with the
 * Menejment page (both value sales at the same snapshot prices).
 */
@Service
@Transactional(readOnly = true)
public class FinancialStatementService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal EPS = new BigDecimal("0.05");
    /** Far enough back to capture every posting for an "as of" / opening view. */
    private static final LocalDate EARLY = LocalDate.of(2000, 1, 1);

    private final JournalLineRepository lines;
    private final JournalEntryRepository entries;
    private final ChartOfAccountsService chart;

    public FinancialStatementService(JournalLineRepository lines, JournalEntryRepository entries,
                                     ChartOfAccountsService chart) {
        this.lines = lines;
        this.entries = entries;
        this.chart = chart;
    }

    // ======================================================== trial balance

    public TrialBalanceResponse trialBalance(LocalDate from, LocalDate to) {
        Map<Long, GlAccount> byId = accountsById();
        Map<Long, Agg> agg = aggregate(from, to);
        List<TrialBalanceRow> rows = new ArrayList<>();
        BigDecimal totalDebit = ZERO;
        BigDecimal totalCredit = ZERO;
        for (GlAccount a : sortedByCode(byId)) {
            Agg g = agg.get(a.getId());
            if (g == null || (g.debit.signum() == 0 && g.credit.signum() == 0)) {
                continue;
            }
            BigDecimal balance = g.debit.subtract(g.credit);
            rows.add(new TrialBalanceRow(a.getId(), a.getCode(), a.getName(), a.getType(),
                    g.debit, g.credit, balance));
            totalDebit = totalDebit.add(g.debit);
            totalCredit = totalCredit.add(g.credit);
        }
        boolean balanced = totalDebit.subtract(totalCredit).abs().compareTo(EPS) <= 0;
        return new TrialBalanceResponse(from, to, rows, totalDebit, totalCredit, balanced);
    }

    // ============================================================= P&L

    public ProfitLossResponse profitLoss(LocalDate from, LocalDate to) {
        Map<Long, GlAccount> byId = accountsById();
        Map<Long, Agg> agg = aggregate(from, to);

        List<StatementLine> revenue = new ArrayList<>();
        List<StatementLine> cogs = new ArrayList<>();
        List<StatementLine> expenses = new ArrayList<>();
        BigDecimal revenueTotal = ZERO;
        BigDecimal cogsTotal = ZERO;
        BigDecimal expenseTotal = ZERO;

        for (GlAccount a : sortedByCode(byId)) {
            Agg g = agg.get(a.getId());
            if (g == null) {
                continue;
            }
            if (a.getType() == GlAccountType.REVENUE) {
                BigDecimal net = g.credit.subtract(g.debit);   // contra (discounts) net down
                if (net.signum() != 0) {
                    revenue.add(new StatementLine(a.getCode(), a.getName(), net));
                    revenueTotal = revenueTotal.add(net);
                }
            } else if (a.getType() == GlAccountType.EXPENSE) {
                BigDecimal amt = g.debit.subtract(g.credit);
                if (amt.signum() == 0) {
                    continue;
                }
                if (a.getCode().startsWith("5")) {   // 5xxx = cost of goods / direct
                    cogs.add(new StatementLine(a.getCode(), a.getName(), amt));
                    cogsTotal = cogsTotal.add(amt);
                } else {                             // 6xxx = operating expenses
                    expenses.add(new StatementLine(a.getCode(), a.getName(), amt));
                    expenseTotal = expenseTotal.add(amt);
                }
            }
        }
        BigDecimal grossProfit = revenueTotal.subtract(cogsTotal);
        BigDecimal netProfit = grossProfit.subtract(expenseTotal);
        return new ProfitLossResponse(from, to, revenue, revenueTotal,
                cogs, cogsTotal, grossProfit, expenses, expenseTotal, netProfit);
    }

    // ====================================================== balance sheet

    public BalanceSheetResponse balanceSheet(LocalDate asOf) {
        Map<Long, GlAccount> byId = accountsById();
        Map<Long, Agg> agg = aggregate(EARLY, asOf);

        List<StatementLine> assets = new ArrayList<>();
        List<StatementLine> liabilities = new ArrayList<>();
        List<StatementLine> equity = new ArrayList<>();
        BigDecimal assetTotal = ZERO;
        BigDecimal liabilityTotal = ZERO;
        BigDecimal equityCapitalTotal = ZERO;
        BigDecimal retainedEarnings = ZERO;
        BigDecimal revenueNet = ZERO;
        BigDecimal expenseNet = ZERO;

        for (GlAccount a : sortedByCode(byId)) {
            Agg g = agg.get(a.getId());
            if (g == null) {
                continue;
            }
            switch (a.getType()) {
                case ASSET -> {
                    BigDecimal bal = g.debit.subtract(g.credit);
                    if (bal.signum() != 0) {
                        assets.add(new StatementLine(a.getCode(), a.getName(), bal));
                        assetTotal = assetTotal.add(bal);
                    }
                }
                case LIABILITY -> {
                    BigDecimal bal = g.credit.subtract(g.debit);
                    if (bal.signum() != 0) {
                        liabilities.add(new StatementLine(a.getCode(), a.getName(), bal));
                        liabilityTotal = liabilityTotal.add(bal);
                    }
                }
                case EQUITY -> {
                    BigDecimal bal = g.credit.subtract(g.debit);
                    if (a.getCode().equals(ChartOfAccountsService.RETAINED_EARNINGS)) {
                        retainedEarnings = retainedEarnings.add(bal);
                    }
                    if (bal.signum() != 0) {
                        equity.add(new StatementLine(a.getCode(), a.getName(), bal));
                        equityCapitalTotal = equityCapitalTotal.add(bal);
                    }
                }
                case REVENUE -> revenueNet = revenueNet.add(g.credit.subtract(g.debit));
                case EXPENSE -> expenseNet = expenseNet.add(g.debit.subtract(g.credit));
            }
        }
        // P&L accounts are not closed into equity, so current net income is
        // shown explicitly and added to the equity section to make it balance.
        BigDecimal netIncomeToDate = revenueNet.subtract(expenseNet);
        BigDecimal equityTotal = equityCapitalTotal.add(netIncomeToDate);
        BigDecimal liabilitiesPlusEquity = liabilityTotal.add(equityTotal);
        boolean balanced = assetTotal.subtract(liabilitiesPlusEquity).abs().compareTo(EPS) <= 0;
        return new BalanceSheetResponse(asOf, assets, assetTotal, liabilities, liabilityTotal,
                equity, equityCapitalTotal, retainedEarnings, netIncomeToDate,
                equityTotal, liabilitiesPlusEquity, balanced);
    }

    // ========================================================== cash flow

    public CashFlowResponse cashFlow(LocalDate from, LocalDate to) {
        Map<String, GlAccount> byCode = chart.byCode();
        Long cashId = byCode.get(ChartOfAccountsService.CASH).getId();
        Long bankId = byCode.get(ChartOfAccountsService.BANK).getId();

        BigDecimal opening = cashBalanceAsOf(from.minusDays(1), cashId, bankId);
        BigDecimal closing = cashBalanceAsOf(to, cashId, bankId);

        Map<String, BigDecimal> inflow = new LinkedHashMap<>();
        Map<String, BigDecimal> outflow = new LinkedHashMap<>();
        for (JournalEntry e : entries.findByEntryDateBetweenOrderByEntryDateDescIdDesc(from, to)) {
            BigDecimal cashDelta = ZERO;
            for (JournalLine l : e.getLines()) {
                if (cashId.equals(l.getAccountId()) || bankId.equals(l.getAccountId())) {
                    cashDelta = cashDelta.add(nz(l.getDebit())).subtract(nz(l.getCredit()));
                }
            }
            if (cashDelta.signum() == 0) {
                continue;
            }
            String label = sourceLabel(e.getSource());
            if (cashDelta.signum() > 0) {
                inflow.merge(label, cashDelta, BigDecimal::add);
            } else {
                outflow.merge(label, cashDelta.negate(), BigDecimal::add);
            }
        }
        List<CashFlowLine> inflows = toLines(inflow);
        List<CashFlowLine> outflows = toLines(outflow);
        BigDecimal inflowTotal = sum(inflow.values());
        BigDecimal outflowTotal = sum(outflow.values());
        return new CashFlowResponse(from, to, opening, closing,
                closing.subtract(opening), inflows, inflowTotal, outflows, outflowTotal);
    }

    private BigDecimal cashBalanceAsOf(LocalDate asOf, Long cashId, Long bankId) {
        Map<Long, Agg> agg = aggregate(EARLY, asOf);
        BigDecimal bal = ZERO;
        for (Long id : List.of(cashId, bankId)) {
            Agg g = agg.get(id);
            if (g != null) {
                bal = bal.add(g.debit.subtract(g.credit));
            }
        }
        return bal;
    }

    // =============================================================== helpers

    private record Agg(BigDecimal debit, BigDecimal credit) {
    }

    private Map<Long, Agg> aggregate(LocalDate from, LocalDate to) {
        List<Long> scope = TenantContext.activeScope();
        Map<Long, Agg> map = new HashMap<>();
        if (scope.isEmpty()) {
            return map;
        }
        for (Object[] r : lines.aggregateByAccount(scope, from, to)) {
            Long aid = ((Number) r[0]).longValue();
            map.put(aid, new Agg(toBd(r[1]), toBd(r[2])));
        }
        return map;
    }

    private Map<Long, GlAccount> accountsById() {
        Map<Long, GlAccount> byId = new HashMap<>();
        for (GlAccount a : chart.byCode().values()) {
            byId.put(a.getId(), a);
        }
        return byId;
    }

    private static List<GlAccount> sortedByCode(Map<Long, GlAccount> byId) {
        List<GlAccount> list = new ArrayList<>(byId.values());
        list.sort((x, y) -> x.getCode().compareTo(y.getCode()));
        return list;
    }

    private static List<CashFlowLine> toLines(Map<String, BigDecimal> m) {
        List<CashFlowLine> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(new CashFlowLine(k, v)));
        return out;
    }

    private static BigDecimal sum(Iterable<BigDecimal> vals) {
        BigDecimal s = ZERO;
        for (BigDecimal v : vals) {
            s = s.add(v);
        }
        return s;
    }

    private static String sourceLabel(JournalSource s) {
        return switch (s) {
            case SALE -> "Sotuvlardan tushum";
            case SALE_REFUND -> "Tovar qaytarish";
            case PAYMENT -> "To'lovlar";
            case EXPENSE -> "Do'kon xarajatlari";
            case HOME_EXPENSE -> "Boshqa xarajatlar";
            case MANAGEMENT_COST -> "Boshqaruv (ish haqi/soliq)";
            case STOCK_IN -> "Tovar xaridi";
            case OPENING_BALANCE -> "Boshlang'ich qoldiq";
            default -> "Boshqa";
        };
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) {
            return ZERO;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(o.toString());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }
}
