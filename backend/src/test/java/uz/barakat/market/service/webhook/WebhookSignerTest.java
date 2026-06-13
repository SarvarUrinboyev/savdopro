package uz.barakat.market.service.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** HMAC-SHA256 signature is deterministic and matches a known vector. */
class WebhookSignerTest {

    @Test
    void signsWithKnownVector() {
        // RFC-style known value: HMAC-SHA256(key="key", msg="The quick brown fox jumps over the lazy dog")
        String sig = WebhookSigner.sign("key", "The quick brown fox jumps over the lazy dog");
        assertThat(sig).isEqualTo("sha256=f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void sameInputSameSignature() {
        String a = WebhookSigner.sign("whsec_abc", "{\"event\":\"sale.created\"}");
        String b = WebhookSigner.sign("whsec_abc", "{\"event\":\"sale.created\"}");
        assertThat(a).isEqualTo(b).startsWith("sha256=");
    }

    @Test
    void differentSecretDifferentSignature() {
        String a = WebhookSigner.sign("secret-1", "body");
        String b = WebhookSigner.sign("secret-2", "body");
        assertThat(a).isNotEqualTo(b);
    }
}
