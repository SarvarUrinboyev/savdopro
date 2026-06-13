package uz.barakat.market.domain;

/** Whether an accounting period accepts new postings. */
public enum PeriodStatus {
    /** Postings allowed. */
    OPEN,
    /** Locked — no entry may be created, edited or deleted within its dates. */
    CLOSED
}
