package uz.barakat.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.domain.OnlinePayment;
import uz.barakat.market.domain.Sale;
import uz.barakat.market.dto.CustomerResponse;
import uz.barakat.market.dto.ReconciliationDtos.OnlineRow;
import uz.barakat.market.dto.ReconciliationDtos.OnlineSummary;
import uz.barakat.market.dto.ReconciliationDtos.ReconciliationResponse;
import uz.barakat.market.dto.ReconciliationDtos.TerminalDay;
import uz.barakat.market.dto.ReconciliationDtos.TerminalSummary;
import uz.barakat.market.dto.TerminalResponse;
import uz.barakat.market.repository.OnlinePaymentRepository;
import uz.barakat.market.repository.SaleRepository;

/**
 * Automatic bank/payments reconciliation: matches incoming money against the
 * shop's own records, in canonical USD where the data is USD and in UZS so'm
 * for the card-terminal settlement (which the bank reports in so'm).
 *
 * <ul>
 *   <li><b>Online (Click/Payme) → debt.</b> Each {@link OnlinePayment} is
 *       already auto-credited to the customer's ledger when performed (it
 *       carries a {@code ledgerTxId}); we surface that match and flag the
 *       exceptions: PENDING (created, unpaid), UNRECONCILED (paid but never
 *       credited) and CANCELLED.</li>
 *   <li><b>Terminal (bank) → card sales.</b> Per day, the Humo+UzCard total
 *       the bank settled vs the POS KARTA sales converted to so'm; a gap
 *       beyond tolerance is a DISCREPANCY.</li>
 * </ul>
 *
 * Read-only; the tenant filter is active (class is {@code @Transactional}) so
 * sale / customer reads are shop-scoped. {@link OnlinePayment} is not
 * tenant-filtered, so it is queried by explicit shop id.
 */
@Service
@Transactional(readOnly = true)
public class ReconciliationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    /** Allowed gap between terminal & POS card totals before flagging (FX + rounding). */
    private static final BigDecimal TERMINAL_TOLERANCE_PCT = new BigDecimal("0.02");
    private static final BigDecimal TERMINAL_TOLERANCE_FLOOR = new BigDecimal("1000"); // so'm

    private final OnlinePaymentRepository onlinePayments;
    private final SaleRepository sales;
    private final CustomerService customers;
    private final TerminalService terminals;
    private final MoneyConverter converter;

    public ReconciliationService(OnlinePaymentRepository onlinePayments, SaleRepository sales,
                                 CustomerService customers, TerminalService terminals,
                                 MoneyConverter converter) {
        this.onlinePayments = onlinePayments;
        this.sales = sales;
        this.customers = customers;
        this.terminals = terminals;
        this.converter = converter;
    }

    public ReconciliationResponse reconcile(LocalDate from, LocalDate to) {
        Online o = online(from, to);
        Terminal t = terminal(from, to);
        return new ReconciliationResponse(from, to, o.rows(), o.summary(), t.rows(), t.summary());
    }

    // ---------------------------------------------------- online (Click/Payme)

    private record Online(List<OnlineRow> rows, OnlineSummary summary) { }

    private Online online(LocalDate from, LocalDate to) {
        Long shopId = activeShop();
        if (shopId == null) {
            return new Online(List.of(), new OnlineSummary(0, 0, 0, 0, 0, ZERO, ZERO));
        }
        long fromMs = epochMs(from);
        long toMs = epochMs(to.plusDays(1));
        Map<Long, CustomerResponse> custById = new HashMap<>();
        for (CustomerResponse c : customers.list()) {
            custById.put(c.id(), c);
        }

        List<OnlineRow> rows = new ArrayList<>();
        int matched = 0;
        int pending = 0;
        int unrec = 0;
        int cancelled = 0;
        BigDecimal matchedUsd = ZERO;
        BigDecimal unrecUsd = ZERO;

        for (OnlinePayment op : onlinePayments.findByShopIdOrderByIdDesc(shopId)) {
            Long t = op.getCreateTimeMs();
            if (t == null || t < fromMs || t >= toMs) {
                continue;
            }
            CustomerResponse c = custById.get(op.getCustomerId());
            String status;
            String note = null;
            switch (op.getState()) {
                case OnlinePayment.STATE_PERFORMED -> {
                    if (op.getLedgerTxId() != null) {
                        status = "MATCHED";
                        matched++;
                        matchedUsd = matchedUsd.add(nz(op.getAmount()));
                        if (c != null && c.balance() != null && c.balance().signum() < 0) {
                            note = "Mijoz balansi manfiy — ortiqcha to'lov bo'lishi mumkin";
                        }
                    } else {
                        status = "UNRECONCILED";
                        unrec++;
                        unrecUsd = unrecUsd.add(nz(op.getAmount()));
                        note = "To'langan, lekin qarzga yozilmagan";
                    }
                }
                case OnlinePayment.STATE_CREATED -> { status = "PENDING"; pending++; }
                default -> { status = "CANCELLED"; cancelled++; }
            }
            rows.add(new OnlineRow(op.getId(), op.getProvider(), op.getProviderTxnId(),
                    op.getCustomerId(), c == null ? null : c.name(), nz(op.getAmount()),
                    status, op.getPerformTimeMs(), note));
        }
        return new Online(rows, new OnlineSummary(
                rows.size(), matched, pending, unrec, cancelled, matchedUsd, unrecUsd));
    }

    // ----------------------------------------------------- terminal (bank) vs POS

    private record Terminal(List<TerminalDay> rows, TerminalSummary summary) { }

    private Terminal terminal(LocalDate from, LocalDate to) {
        BigDecimal rate = converter.usdToUzs();
        Map<LocalDate, BigDecimal> cardUsdByDay = cardSalesUsdByDay(from, to);

        List<TerminalDay> rows = new ArrayList<>();
        BigDecimal termTotal = ZERO;
        BigDecimal posTotal = ZERO;
        int discrepancyDays = 0;

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            TerminalResponse tr = terminals.forDate(d);
            BigDecimal termSom = nz(tr.humoAmount()).add(nz(tr.uzcardAmount()));
            BigDecimal posSom = nz(cardUsdByDay.get(d))
                    .multiply(rate).setScale(0, RoundingMode.HALF_UP);
            if (termSom.signum() == 0 && posSom.signum() == 0) {
                continue;   // nothing to reconcile this day
            }
            BigDecimal diff = termSom.subtract(posSom);
            BigDecimal tolerance = posSom.max(termSom)
                    .multiply(TERMINAL_TOLERANCE_PCT).max(TERMINAL_TOLERANCE_FLOOR);
            boolean balanced = diff.abs().compareTo(tolerance) <= 0;
            if (!balanced) {
                discrepancyDays++;
            }
            rows.add(new TerminalDay(d, termSom, posSom, diff,
                    balanced ? "BALANCED" : "DISCREPANCY"));
            termTotal = termTotal.add(termSom);
            posTotal = posTotal.add(posSom);
        }
        rows.sort((a, b) -> b.date().compareTo(a.date()));   // newest first
        return new Terminal(rows, new TerminalSummary(
                termTotal, posTotal, termTotal.subtract(posTotal), discrepancyDays));
    }

    /** Net (after refunds) POS KARTA sales per day, in USD-value. */
    private Map<LocalDate, BigDecimal> cardSalesUsdByDay(LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> byDay = new HashMap<>();
        List<Sale> list = sales.findByCreatedAtBetweenOrderByCreatedAtDesc(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        for (Sale s : list) {
            if (!"KARTA".equalsIgnoreCase(s.getPaymentMethod()) || s.getCreatedAt() == null) {
                continue;
            }
            BigDecimal net = nz(s.getTotalUzs()).subtract(nz(s.getRefundedTotalUzs()));
            byDay.merge(s.getCreatedAt().toLocalDate(), net, BigDecimal::add);
        }
        return byDay;
    }

    // --------------------------------------------------------------- helpers

    private static Long activeShop() {
        Long single = TenantContext.currentShopId();
        if (single != null) {
            return single;
        }
        List<Long> scope = TenantContext.activeScope();
        return scope.isEmpty() ? null : scope.get(0);
    }

    private static long epochMs(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }
}
