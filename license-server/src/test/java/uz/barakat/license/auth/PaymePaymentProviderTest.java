package uz.barakat.license.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import uz.barakat.license.domain.Payment;

class PaymePaymentProviderTest {

    private static final String BASE = "https://checkout.paycom.uz";

    @Test
    void unconfiguredWhenMerchantIdOrKeyBlank() {
        assertThat(new PaymePaymentProvider("", "", "order_id", BASE).isConfigured()).isFalse();
        assertThat(new PaymePaymentProvider("m1", "", "order_id", BASE).isConfigured()).isFalse();
        assertThat(new PaymePaymentProvider("", "k1", "order_id", BASE).isConfigured()).isFalse();
    }

    @Test
    void checkoutUrlBase64EncodesMerchantOrderAndTiyinAmount() {
        PaymePaymentProvider p = new PaymePaymentProvider("merch1", "key", "order_id", BASE);
        assertThat(p.isConfigured()).isTrue();
        assertThat(p.name()).isEqualTo("PAYME");

        Payment pay = new Payment();
        pay.setId(42L);
        pay.setAmountUzs(99_000L);

        String url = p.checkoutUrl(pay);
        assertThat(url).startsWith(BASE + "/");

        String encoded = url.substring((BASE + "/").length());
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        // 99 000 so'm → 9 900 000 tiyin
        assertThat(decoded).isEqualTo("m=merch1;ac.order_id=42;a=9900000");
    }
}
