package uz.barakat.market.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Click / Payme merchant configuration, bound from {@code payment.*}.
 * Both providers are disabled until real merchant credentials are set; while
 * disabled, link generation refuses and the webhooks reject every call, so
 * there is zero risk in production before the merchant is onboarded and the
 * integration is certified in each provider's sandbox.
 *
 * <p>Real values go in {@code application-local.properties} or env vars —
 * never commit them.</p>
 */
@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(Payme payme, Click click) {

    public PaymentProperties {
        if (payme == null) {
            payme = new Payme(false, null, null, null, null);
        }
        if (click == null) {
            click = new Click(false, null, null, null, null, null);
        }
    }

    /** Payme (Paycom) merchant cashbox. Auth: Basic "Paycom:{key}". */
    public record Payme(boolean enabled, String merchantId, String key,
                        String checkoutUrl, String account) {
        public Payme {
            if (checkoutUrl == null || checkoutUrl.isBlank()) {
                checkoutUrl = "https://checkout.paycom.uz";
            }
            if (account == null || account.isBlank()) {
                account = "customer_id";
            }
        }

        public boolean isUsable() {
            return enabled
                    && notBlank(merchantId) && !merchantId.startsWith("PUT-")
                    && notBlank(key) && !key.startsWith("PUT-");
        }
    }

    /** Click merchant (SHOP API). Sign: MD5 over the documented field order. */
    public record Click(boolean enabled, String serviceId, String merchantId,
                        String secretKey, String merchantUserId, String checkoutUrl) {
        public Click {
            if (checkoutUrl == null || checkoutUrl.isBlank()) {
                checkoutUrl = "https://my.click.uz/services/pay";
            }
        }

        public boolean isUsable() {
            return enabled
                    && notBlank(serviceId) && notBlank(merchantId)
                    && notBlank(secretKey) && !secretKey.startsWith("PUT-");
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
