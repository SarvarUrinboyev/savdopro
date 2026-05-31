package uz.barakat.license.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uz.barakat.license.domain.Payment;

/**
 * Payme checkout: redirect the merchant to the Payme hosted-checkout page,
 * whose path is the base64 of {@code m=<merchant>;ac.<field>=<order>;a=<tiyin>}.
 * Payme then drives the JSON-RPC handshake against {@link PaymeController}.
 * Amounts go to Payme in tiyin (so'm × 100).
 */
@Component
public class PaymePaymentProvider implements PaymentProvider {

    private final String merchantId;
    private final String accountField;
    private final String checkoutBaseUrl;
    private final boolean configured;

    public PaymePaymentProvider(
            @Value("${billing.payme.merchant-id:}") String merchantId,
            @Value("${billing.payme.merchant-key:}") String merchantKey,
            @Value("${billing.payme.account-field:order_id}") String accountField,
            @Value("${billing.payme.checkout-base-url:https://checkout.paycom.uz}")
                    String checkoutBaseUrl) {
        this.merchantId = merchantId;
        this.accountField = accountField;
        this.checkoutBaseUrl = checkoutBaseUrl;
        // merchant-key isn't used to build the URL, but PaymeController needs it
        // to authenticate callbacks, so checkout is only meaningful with both.
        this.configured = notBlank(merchantId) && notBlank(merchantKey);
    }

    @Override
    public String name() {
        return "PAYME";
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String checkoutUrl(Payment payment) {
        long tiyin = payment.getAmountUzs() * 100L;
        String raw = "m=" + merchantId
                + ";ac." + accountField + "=" + payment.getId()
                + ";a=" + tiyin;
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return checkoutBaseUrl + "/" + encoded;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
