package uz.barakat.market.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import uz.barakat.market.service.LedgerEvents.ExpenseRecorded;
import uz.barakat.market.service.LedgerEvents.HomeExpenseRecorded;
import uz.barakat.market.service.LedgerEvents.ManagementCostRecorded;
import uz.barakat.market.service.LedgerEvents.PaymentRecorded;
import uz.barakat.market.service.LedgerEvents.SalePosted;
import uz.barakat.market.service.LedgerEvents.SaleRefunded;
import uz.barakat.market.service.LedgerEvents.StockMovementRecorded;

/**
 * Bridges operational events to the ledger, strictly after the source
 * transaction commits. Every handler is best-effort: a posting failure is
 * logged and swallowed so it can never roll back or surface on the sale /
 * expense that triggered it. The actual posting runs in its own transaction
 * inside {@link LedgerPostingService} (where the tenant filter is enabled),
 * and any exception propagates out of that proxied call into the catch here —
 * so a rolled-back posting leaves the ledger clean, not half-written.
 *
 * <p>Runs synchronously on the request thread, so the request's TenantContext
 * (and security context) are still set when the entity is re-loaded + posted.
 */
@Component
public class LedgerPostingListener {

    private static final Logger log = LoggerFactory.getLogger(LedgerPostingListener.class);

    private final LedgerPostingService posting;

    public LedgerPostingListener(LedgerPostingService posting) {
        this.posting = posting;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSale(SalePosted ev) {
        safely("sale " + ev.saleId(), () -> posting.handleSale(ev.saleId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSaleRefund(SaleRefunded ev) {
        safely("refund " + ev.saleId(), () -> posting.handleSaleRefund(ev));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onExpense(ExpenseRecorded ev) {
        safely("expense " + ev.expenseId(), () -> posting.handleExpense(ev.expenseId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onHomeExpense(HomeExpenseRecorded ev) {
        safely("home-expense " + ev.homeExpenseId(),
                () -> posting.handleHomeExpense(ev.homeExpenseId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onManagementCost(ManagementCostRecorded ev) {
        safely("mgmt-cost " + ev.costId(), () -> posting.handleManagementCost(ev.costId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPayment(PaymentRecorded ev) {
        safely("payment " + ev.paymentId(), () -> posting.handlePayment(ev.paymentId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStockMovement(StockMovementRecorded ev) {
        safely("stock-movement " + ev.movementId(),
                () -> posting.handleStockMovement(ev.movementId()));
    }

    private void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            // Ledger posting is best-effort. The sale/expense already committed;
            // we never let a bookkeeping problem affect it. Re-runnable via backfill.
            log.warn("Ledger auto-post failed for {} — skipped (backfill can recover): {}",
                    what, ex.toString());
        }
    }
}
