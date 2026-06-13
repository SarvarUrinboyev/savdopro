package uz.barakat.market.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Domain events published by the operational services and consumed — strictly
 * AFTER the originating transaction commits — by {@link LedgerPostingListener}.
 * This decoupling is deliberate: a ledger-posting failure can never roll back
 * or slow down the sale / expense that triggered it.
 */
public final class LedgerEvents {

    private LedgerEvents() {
    }

    /** A POS sale was committed. The listener loads the sale and posts it. */
    public record SalePosted(Long saleId) {
    }

    /** A POS refund was committed (may be partial; carries this refund's delta). */
    public record SaleRefunded(
            Long shopId, Long saleId, LocalDate date, String paymentMethod,
            BigDecimal refundedAmountUsd, List<RefundLine> returnedLines, String uniqueRef) {
    }

    /** One returned line: how many units of a product went back into stock. */
    public record RefundLine(Long productId, int quantity) {
    }

    /** A market expense row was created. */
    public record ExpenseRecorded(Long expenseId) {
    }

    /** A home / owner expense row was created. */
    public record HomeExpenseRecorded(Long homeExpenseId) {
    }

    /** A management cost (salary / tax / other) was created. */
    public record ManagementCostRecorded(Long costId) {
    }

    /** A manual payment-journal row was created (not a POS payment). */
    public record PaymentRecorded(Long paymentId) {
    }

    /** A warehouse stock movement was created (intake / correction / write-off). */
    public record StockMovementRecorded(Long movementId) {
    }
}
