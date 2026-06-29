package uz.barakat.market.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.JournalSource;
import uz.barakat.market.domain.Payment;
import uz.barakat.market.domain.Sale;
import uz.barakat.market.domain.SaleItem;
import uz.barakat.market.dto.AccountingDtos.BackfillResponse;
import uz.barakat.market.repository.ExpenseRepository;
import uz.barakat.market.repository.HomeExpenseRepository;
import uz.barakat.market.repository.JournalEntryRepository;
import uz.barakat.market.repository.ManagementCostRepository;
import uz.barakat.market.repository.PaymentRepository;
import uz.barakat.market.repository.SaleRepository;
import uz.barakat.market.repository.StockMovementRepository;
import uz.barakat.market.service.LedgerEvents.RefundLine;

/**
 * One-shot replay that materialises the ledger from the existing operational
 * tables for the current shop. Idempotent — every posting is guarded by
 * (source, source_ref), so re-running only fills the gaps. Intended to be run
 * once after enabling the accounting module (or to recover after an outage).
 */
@Service
@Transactional
public class LedgerBackfillService {

    private static final Logger log = LoggerFactory.getLogger(LedgerBackfillService.class);

    private final LedgerPostingService posting;
    private final JournalEntryRepository entries;
    private final SaleRepository sales;
    private final StockMovementRepository movements;
    private final ExpenseRepository expenses;
    private final HomeExpenseRepository homeExpenses;
    private final ManagementCostRepository mgmtCosts;
    private final PaymentRepository payments;

    public LedgerBackfillService(LedgerPostingService posting, JournalEntryRepository entries,
                                 SaleRepository sales, StockMovementRepository movements,
                                 ExpenseRepository expenses, HomeExpenseRepository homeExpenses,
                                 ManagementCostRepository mgmtCosts, PaymentRepository payments) {
        this.posting = posting;
        this.entries = entries;
        this.movements = movements;
        this.sales = sales;
        this.expenses = expenses;
        this.homeExpenses = homeExpenses;
        this.mgmtCosts = mgmtCosts;
        this.payments = payments;
    }

    /** Replays all history for the active shop. Safe to call repeatedly. */
    public BackfillResponse run() {
        long before = entries.count();
        int candidates = 0;

        // 1) Warehouse stock movements (intake / correction / write-off).
        //    SALE / RETURN are skipped inside postStockMovement.
        for (var m : movements.findAll()) {
            candidates++;
            posting.postStockMovement(m);
        }

        // 2) Sales — revenue + cash/receivable + discount + COGS.
        List<Sale> allSales = sales.findAll();
        for (Sale s : allSales) {
            candidates++;
            posting.postSale(s);
            // 2b) Refunds: one cumulative entry per refunded sale, but only if a
            //     live per-event refund ("SR:<id>:<millis>") hasn't already posted.
            if (s.getRefundedTotalUzs() != null && s.getRefundedTotalUzs().signum() > 0) {
                String prefix = "SR:" + s.getId();
                if (!entries.existsBySourceAndSourceRefStartingWith(JournalSource.SALE_REFUND, prefix)) {
                    candidates++;
                    posting.postSaleRefund(s.getShopId(), refundDate(s), s.getPaymentMethod(),
                            s.getRefundedTotalUzs(), returnedLines(s), prefix);
                }
            }
        }

        // 3) Expenses + home expenses + management costs.
        for (var x : expenses.findAll()) {
            candidates++;
            posting.postExpense(x);
        }
        for (var x : homeExpenses.findAll()) {
            candidates++;
            posting.postHomeExpense(x);
        }
        for (var c : mgmtCosts.findAll()) {
            candidates++;
            posting.postManagementCost(c);
        }

        // 4) Manual payments only — exclude the ones a sale created (linked by
        //    payment_id) or a refund created (note "Refund sale#…"), since those
        //    are already captured by the sale / refund postings above.
        Set<Long> salePaymentIds = new HashSet<>();
        for (Sale s : allSales) {
            if (s.getPaymentId() != null) {
                salePaymentIds.add(s.getPaymentId());
            }
        }
        for (Payment p : payments.findAll()) {
            if (salePaymentIds.contains(p.getId()) || isPosGenerated(p)) {
                continue;
            }
            candidates++;
            posting.postPayment(p);
        }

        long created = entries.count() - before;
        int skipped = (int) Math.max(0, candidates - created);
        log.info("Ledger backfill: {} candidates, {} created, {} skipped",
                candidates, created, skipped);
        return new BackfillResponse((int) created, skipped,
                "Bosh kitob tarixiy ma'lumotlardan to'ldirildi");
    }

    private static boolean isPosGenerated(Payment p) {
        String note = p.getNote();
        return note != null && (note.startsWith("POS sale") || note.startsWith("Refund sale#"));
    }

    private static LocalDate refundDate(Sale s) {
        if (s.getRefundedAt() != null) {
            return s.getRefundedAt().toLocalDate();
        }
        return s.getCreatedAt() != null ? s.getCreatedAt().toLocalDate() : LocalDate.now();
    }

    private static List<RefundLine> returnedLines(Sale s) {
        List<RefundLine> out = new ArrayList<>();
        for (SaleItem it : s.getItems()) {
            if (it.getRefundedQty() > 0) {
                out.add(new RefundLine(
                        it.getProductId(), it.getRefundedQty(), it.getCostAtSaleUzs()));
            }
        }
        // Fully-on-credit / legacy sales may have no per-line refund detail; the
        // cash + returns side still posts, only the COGS reversal is then zero.
        return out;
    }

}
