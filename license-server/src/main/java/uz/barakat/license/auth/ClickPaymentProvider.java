package uz.barakat.license.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import uz.barakat.license.domain.Payment;

/**
 * Click checkout: redirect the merchant to my.click.uz with the order's id
 * as {@code transaction_param}. Click then drives the two-stage
 * Prepare/Complete handshake against {@link ClickController}. Amounts go to
 * Click in so'm (the same unit we store on the payment row).
 */
@Component
public class ClickPaymentProvider implements PaymentProvider {

    private final String serviceId;
    private final String merchantId;
    private final String checkoutBaseUrl;
    private final String returnUrl;
    private final boolean configured;

    public ClickPaymentProvider(
            @Value("${billing.click.service-id:}") String serviceId,
            @Value("${billing.click.merchant-id:}") String merchantId,
            @Value("${billing.click.secret-key:}") String secretKey,
            @Value("${billing.click.checkout-base-url:https://my.click.uz/services/pay}")
                    String checkoutBaseUrl,
            @Value("${billing.click.return-url:}") String returnUrl) {
        this.serviceId = serviceId;
        this.merchantId = merchantId;
        this.checkoutBaseUrl = checkoutBaseUrl;
        this.returnUrl = returnUrl;
        // The secret is only used by ClickPaymentService for signature checks,
        // but all three of service-id / merchant-id / secret-key must be set
        // for checkout to be meaningful, so we gate on the full triple here.
        this.configured = notBlank(serviceId) && notBlank(merchantId) && notBlank(secretKey);
    }

    @Override
    public String name() {
        return "CLICK";
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String checkoutUrl(Payment payment) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(checkoutBaseUrl)
                .queryParam("service_id", serviceId)
                .queryParam("merchant_id", merchantId)
                .queryParam("amount", payment.getAmountUzs())
                .queryParam("transaction_param", payment.getId());
        if (notBlank(returnUrl)) {
            b.queryParam("return_url", returnUrl);
        }
        return b.build().toUriString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
