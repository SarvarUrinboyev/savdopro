package uz.barakat.license.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The MD5 helper is anchored against well-known vectors (so a broken hex
 * encoding is caught independently), and each sign formula is pinned to
 * Click's documented field order — reorder a field in production code and
 * these fail.
 */
class ClickSignatureTest {

    private static final String SECRET = "S3CR3T";

    @Test
    void md5HexMatchesKnownVectors() {
        assertThat(ClickSignature.md5Hex("abc"))
                .isEqualTo("900150983cd24fb0d6963f7d28e17f72");
        assertThat(ClickSignature.md5Hex(""))
                .isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
    }

    @Test
    void prepareSignatureFollowsClickFieldOrder() {
        ClickCallback cb = new ClickCallback(
                "tx-1", "svc-9", "pd", "42", null,
                "99000", "0", "0", "", "2026-01-01 10:00:00", "ignored");
        // md5(click_trans_id + service_id + SECRET + merchant_trans_id + amount + action + sign_time)
        String expected = ClickSignature.md5Hex(
                "tx-1" + "svc-9" + SECRET + "42" + "99000" + "0" + "2026-01-01 10:00:00");
        assertThat(ClickSignature.expectedPrepare(cb, SECRET)).isEqualTo(expected);
    }

    @Test
    void completeSignatureIncludesMerchantPrepareId() {
        ClickCallback cb = new ClickCallback(
                "tx-1", "svc-9", "pd", "42", "42",
                "99000", "1", "0", "", "2026-01-01 10:05:00", "ignored");
        String expected = ClickSignature.md5Hex(
                "tx-1" + "svc-9" + SECRET + "42" + "42" + "99000" + "1" + "2026-01-01 10:05:00");
        assertThat(ClickSignature.expectedComplete(cb, SECRET)).isEqualTo(expected);
    }

    @Test
    void matchesIsTrueOnlyForIdenticalHashes() {
        String h = ClickSignature.md5Hex("anything");
        assertThat(ClickSignature.matches(h, h)).isTrue();
        assertThat(ClickSignature.matches(h, "deadbeef")).isFalse();
        assertThat(ClickSignature.matches(h, null)).isFalse();
        assertThat(ClickSignature.matches(null, h)).isFalse();
    }
}
