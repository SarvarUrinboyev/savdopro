package uz.barakat.license.domain;

/** Lifecycle of a subscription payment. */
public enum PaymentStatus {
    /** Created when checkout starts; awaiting the PSP callback. */
    PENDING,
    /** Confirmed by the PSP webhook — the subscription was extended. */
    PAID,
    /** PSP reported failure / cancellation. */
    FAILED
}
