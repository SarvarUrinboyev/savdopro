package uz.barakat.market.domain;

/** Lifecycle of a supplier purchase order. */
public enum PurchaseOrderStatus {
    /** Being drafted; editable, not yet sent. */
    DRAFT,
    /** Sent to the supplier; awaiting goods. */
    ORDERED,
    /** Some lines received, not all. */
    PARTIAL,
    /** Every line fully received. */
    RECEIVED,
    /** Cancelled before completion. */
    CANCELLED
}
