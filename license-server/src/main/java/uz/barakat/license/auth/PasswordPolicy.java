package uz.barakat.license.auth;

/**
 * Single source of truth for password strength across signup, password reset
 * and the super-admin user/account flows.
 *
 * <p>Before this existed each DTO carried its own {@code @Size} rule — signup
 * required 9 characters, reset only 6, admin create/reset only 4 — so a weak
 * password rejected on one screen sailed through another. This unifies them.
 *
 * <p>Policy: at least {@value #MIN_LENGTH} characters, with at least one letter
 * and at least one digit. Symbols are allowed (and encouraged) but not
 * required, so an existing strong passphrase keeps working.
 */
public final class PasswordPolicy {

    /** Minimum length enforced everywhere a password is set. */
    public static final int MIN_LENGTH = 9;

    /** Upper bound — rejects absurd inputs instead of hashing a huge string. */
    public static final int MAX_LENGTH = 128;

    /** Localised (Uzbek) validation message, shared by the constraint. */
    public static final String MESSAGE =
            "Parol kamida 9 ta belgidan iborat bo'lib, kamida bitta harf va "
            + "bitta raqamni o'z ichiga olishi kerak";

    private PasswordPolicy() {
    }

    /**
     * @return {@code true} when {@code raw} satisfies the policy. A {@code null}
     *     value returns {@code true} so a separate {@code @NotBlank} owns the
     *     "required" message rather than this rule double-reporting it; a blank
     *     or too-short string fails on length.
     */
    public static boolean isValid(String raw) {
        if (raw == null) {
            return true;
        }
        int len = raw.length();
        if (len < MIN_LENGTH || len > MAX_LENGTH) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < len; i++) {
            char c = raw.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
            if (hasLetter && hasDigit) {
                return true;
            }
        }
        return false;
    }
}
