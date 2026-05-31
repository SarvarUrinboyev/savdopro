package uz.barakat.license.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import uz.barakat.license.domain.Payment;

class ClickPaymentProviderTest {

    private static final String BASE = "https://my.click.uz/services/pay";

    @Test
    void unconfiguredWhenAnyKeyBlank() {
        assertThat(new ClickPaymentProvider("", "", "", BASE, "").isConfigured()).isFalse();
        assertThat(new ClickPaymentProvider("svc", "m", "", BASE, "").isConfigured()).isFalse();
        assertThat(new ClickPaymentProvider("svc", "", "sec", BASE, "").isConfigured()).isFalse();
    }

    @Test
    void configuredWhenFullTripleSet() {
        ClickPaymentProvider p = new ClickPaymentProvider("svc9", "m7", "secret", BASE, "");
        assertThat(p.isConfigured()).isTrue();
        assertThat(p.name()).isEqualTo("CLICK");
    }

    @Test
    void checkoutUrlCarriesOrderParams() {
        ClickPaymentProvider p = new ClickPaymentProvider("svc9", "m7", "secret", BASE, "");
        Payment pay = new Payment();
        pay.setId(42L);
        pay.setAmountUzs(99_000L);

        String url = p.checkoutUrl(pay);

        assertThat(url)
                .startsWith(BASE)
                .contains("service_id=svc9")
                .contains("merchant_id=m7")
                .contains("amount=99000")
                .contains("transaction_param=42");
    }
}
