package uz.barakat.market.domain;

/**
 * Where a journal entry came from. {@link #MANUAL} entries are typed by an
 * accountant; the rest are auto-posted from the operational tables and carry a
 * {@code source_ref} back to the originating row so posting stays idempotent.
 */
public enum JournalSource {
    /** Hand-entered by a user via the Journal page. */
    MANUAL,
    /** A POS sale: revenue + cash/receivable + discount + COGS. */
    SALE,
    /** A POS refund: sales-return contra + cash back + COGS reversal. */
    SALE_REFUND,
    /** Goods received into the warehouse (DELIVERY / INITIAL / positive correction). */
    STOCK_IN,
    /** Stock written off or counted down (WRITEOFF / negative correction). */
    STOCK_WRITEOFF,
    /** A market expense row. */
    EXPENSE,
    /** A home / owner expense row. */
    HOME_EXPENSE,
    /** A management cost (salary / tax / other). */
    MANAGEMENT_COST,
    /** A manual payment-journal row (supplier payment, debt collection, ...). */
    PAYMENT,
    /** Opening balances captured when the ledger is first set up. */
    OPENING_BALANCE
}
