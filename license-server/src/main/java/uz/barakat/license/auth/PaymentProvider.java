package uz.barakat.license.auth;

import uz.barakat.license.domain.Payment;

/**
 * A payment service provider (PSP) the merchant can be redirected to in
 * order to pay for a subscription. An implementation builds the provider's
 * hosted-checkout URL for a PENDING {@link Payment}; the provider then
 * calls back into its own webhook controller, which verifies the
 * provider-specific signature and flips the payment to PAID via
 * {@link BillingService#confirmPayment} (which extends the subscription).
 *
 * <p>Each provider reads its merchant credentials from configuration
 * ({@code billing.<provider>.*}) and reports {@link #isConfigured()} so
 * checkout can fail fast with a clear message while the keys are still
 * absent — rather than handing the client a dead checkout URL.
 */
public interface PaymentProvider {

    /** Stable id stored on the payment row, e.g. {@code "CLICK"} / {@code "PAYME"}. */
    String name();

    /** True once the merchant credentials for this provider are configured. */
    boolean isConfigured();

    /** The provider's hosted-checkout URL the client should be redirected to. */
    String checkoutUrl(Payment payment);
}
