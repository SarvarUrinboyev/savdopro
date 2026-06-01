package uz.barakat.license.util;

/**
 * Canonical handling of Uzbek phone numbers on the License Server (account
 * contact phones). Mirrors the backend's {@code PhoneUtil}: everything is
 * stored as {@code +998XXXXXXXXX} so duplicate detection is format-agnostic.
 */
public final class PhoneUtil {

    private PhoneUtil() {}

    /** Strips non-digits, drops a leading 998, caps at nine national digits and
     *  re-adds +998. Returns null when there is no national digit. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("998")) digits = digits.substring(3);
        if (digits.length() > 9) digits = digits.substring(0, 9);
        if (digits.isEmpty()) return null;
        return "+998" + digits;
    }
}
