package uz.barakat.market.domain;

/**
 * The five top-level buckets of a chart of accounts. The {@link #normalBalance()}
 * is the side that increases an account of this type — assets and expenses grow
 * with debits, everything else grows with credits.
 */
public enum GlAccountType {
    /** Aktiv — what the business owns (cash, inventory, receivables). */
    ASSET(NormalBalance.DEBIT),
    /** Passiv (majburiyat) — what the business owes (payables, loans). */
    LIABILITY(NormalBalance.CREDIT),
    /** Kapital — owner's equity and retained earnings. */
    EQUITY(NormalBalance.CREDIT),
    /** Daromad — sales revenue and other income. */
    REVENUE(NormalBalance.CREDIT),
    /** Xarajat — cost of goods sold and operating expenses. */
    EXPENSE(NormalBalance.DEBIT);

    private final NormalBalance normalBalance;

    GlAccountType(NormalBalance normalBalance) {
        this.normalBalance = normalBalance;
    }

    public NormalBalance normalBalance() {
        return normalBalance;
    }

    /** True for the P&L account types (revenue / expense) that close each period. */
    public boolean isProfitAndLoss() {
        return this == REVENUE || this == EXPENSE;
    }

    /** True for the balance-sheet account types (asset / liability / equity). */
    public boolean isBalanceSheet() {
        return !isProfitAndLoss();
    }
}
