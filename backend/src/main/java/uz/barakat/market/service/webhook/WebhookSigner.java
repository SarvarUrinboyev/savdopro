package uz.barakat.market.service.webhook;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs webhook payloads so receivers can verify authenticity:
 * {@code X-Barakat-Signature: sha256=<hex HMAC-SHA256(secret, body)>}.
 * Same primitive the Click/Payme inbound callbacks use.
 */
public final class WebhookSigner {

    private WebhookSigner() {
    }

    /** Returns {@code sha256=<hex>} for the given body under the subscription secret. */
    public static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return "sha256=" + sb;
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
        }
    }
}
