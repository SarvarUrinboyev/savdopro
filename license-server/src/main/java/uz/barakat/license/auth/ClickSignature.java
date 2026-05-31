package uz.barakat.license.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Click SHOP-API MD5 signatures. Click signs every Prepare/Complete
 * callback so the merchant can prove it really came from Click:
 *
 * <pre>
 *  prepare:  md5(click_trans_id + service_id + SECRET_KEY
 *                + merchant_trans_id + amount + action + sign_time)
 *  complete: md5(click_trans_id + service_id + SECRET_KEY
 *                + merchant_trans_id + merchant_prepare_id + amount
 *                + action + sign_time)
 * </pre>
 *
 * The hash is over the EXACT strings Click sent, so we never reformat them
 * before hashing. Comparison is constant-time so a mismatching signature
 * can't be brute-forced byte-by-byte via timing.
 */
final class ClickSignature {

    private ClickSignature() {
    }

    static String md5Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 unavailable", e); // never on a JRE
        }
    }

    static String expectedPrepare(ClickCallback cb, String secretKey) {
        return md5Hex(nz(cb.clickTransId()) + nz(cb.serviceId()) + nz(secretKey)
                + nz(cb.merchantTransId()) + nz(cb.amount())
                + nz(cb.action()) + nz(cb.signTime()));
    }

    static String expectedComplete(ClickCallback cb, String secretKey) {
        return md5Hex(nz(cb.clickTransId()) + nz(cb.serviceId()) + nz(secretKey)
                + nz(cb.merchantTransId()) + nz(cb.merchantPrepareId())
                + nz(cb.amount()) + nz(cb.action()) + nz(cb.signTime()));
    }

    static boolean matches(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
