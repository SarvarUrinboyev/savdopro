package uz.barakat.market.util;

/**
 * Canonical handling of Uzbek phone numbers.
 *
 * <p>Every phone entered anywhere in the app is stored as {@code +998XXXXXXXXX}
 * (the +998 country code plus exactly nine national digits). Normalising on the
 * way in means duplicate-detection compares apples to apples regardless of how
 * the operator typed it (spaces, dashes, a leading 0, etc.).
 */
public final class PhoneUtil {

    private PhoneUtil() {}

    /**
     * Strips everything but digits, drops a leading {@code 998}, caps the
     * national part at nine digits and re-adds {@code +998}. Returns
     * {@code null} when there is no national digit at all, so a blank or
     * prefix-only value is persisted as "no phone".
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("998")) digits = digits.substring(3);
        if (digits.length() > 9) digits = digits.substring(0, 9);
        if (digits.isEmpty()) return null;
        return "+998" + digits;
    }
}
