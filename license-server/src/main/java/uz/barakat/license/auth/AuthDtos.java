package uz.barakat.license.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Login / session DTOs grouped in one file to avoid noise. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Login kiritilishi shart") String username,
            @NotBlank(message = "Parol kiritilishi shart") String password,
            /** 6-digit TOTP code — required only when the user has 2FA on. */
            String totpCode) {
    }

    /** Self-service merchant signup — creates a trial account + its owner user. */
    public record RegisterRequest(
            @NotBlank(message = "Biznes nomi kiritilishi shart") String businessName,
            @NotBlank(message = "Ismingiz kiritilishi shart") String fullName,
            @NotBlank(message = "Login kiritilishi shart")
            @Size(min = 3, max = 40, message = "Login 3–40 belgidan iborat bo'lishi kerak")
            String username,
            @NotBlank(message = "Parol kiritilishi shart")
            @Size(min = 6, message = "Parol kamida 6 belgi bo'lishi kerak")
            String password,
            /** Optional contact phone stored on the account. */
            String phone) {
    }

    /** Forgot-password step 1: request a reset code by SMS to a registered phone. */
    public record ForgotPasswordRequest(
            @NotBlank(message = "Telefon raqami kiritilishi shart") String phone) {
    }

    /** Forgot-password step 2: verify the SMS code and set a new password. */
    public record ResetPasswordRequest(
            @NotBlank(message = "Telefon raqami kiritilishi shart") String phone,
            @NotBlank(message = "Kod kiritilishi shart") String code,
            @NotBlank(message = "Yangi parol kiritilishi shart")
            @Size(min = 6, message = "Parol kamida 6 belgi bo'lishi kerak")
            String newPassword) {
    }

    /** Read-only snapshot for the in-app billing / subscription page. */
    public record SubscriptionStatusResponse(
            String plan,
            long monthlyPriceUzs,
            LocalDate subscriptionExpires,
            int daysRemaining,
            boolean expired,
            boolean blocked,
            int maxUsers,
            long currentUsers,
            int maxShops) {
    }

    /** One-time payload returned by the TOTP setup endpoint. */
    public record TotpSetupResponse(String secret, String otpauthUri) { }

    public record TotpVerifyRequest(
            @NotBlank(message = "Kod kiritilishi shart") String code) { }

    /**
     * Phase 4.5 SMS login: request a one-time code for the given phone.
     * The phone must already be on the {@code app_users.phone} column —
     * cold sign-up by phone is intentionally not supported.
     */
    public record SmsRequestRequest(
            @NotBlank(message = "Telefon raqami kiritilishi shart") String phone) { }

    /** Phase 4.5 SMS login: exchange a fresh code for a session. */
    public record SmsVerifyRequest(
            @NotBlank(message = "Telefon raqami kiritilishi shart") String phone,
            @NotBlank(message = "Kod kiritilishi shart") String code) { }

    /**
     * Raw payload Telegram's Login Widget posts. The server verifies the
     * HMAC over these fields and only trusts {@code id} as the linkage
     * key after verification passes — everything else (first/last name,
     * username, photo) is informational and is allowed to mutate.
     */
    public record TelegramAuthRequest(
            String id,
            String firstName,
            String lastName,
            String username,
            String photoUrl,
            String authDate,
            @NotBlank(message = "Telegram imzosi bo'lishi kerak") String hash) {

        /** Project into the field map the verifier expects (snake_case keys). */
        public java.util.Map<String, String> asFieldMap() {
            java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>();
            m.put("id", id);
            if (firstName != null) m.put("first_name", firstName);
            if (lastName != null) m.put("last_name", lastName);
            if (username != null) m.put("username", username);
            if (photoUrl != null) m.put("photo_url", photoUrl);
            if (authDate != null) m.put("auth_date", authDate);
            m.put("hash", hash);
            return m;
        }
    }

    /**
     * Refresh-token rotation payload. The plaintext refresh token never
     * appears in the access JWT — we keep them on separate transports so
     * a stolen JWT alone can't extend itself.
     */
    public record RefreshRequest(
            @NotBlank(message = "Refresh token kiritilishi shart") String refreshToken) {
    }

    /**
     * Login + refresh both return this. {@code accessExpiresInSec} lets
     * the desktop client schedule a silent refresh shortly before the
     * access JWT expires, instead of waiting for a 401 and bouncing
     * the user mid-action.
     */
    public record LoginResponse(
            String token,
            String refreshToken,
            long accessExpiresInSec,
            LocalDateTime refreshExpiresAt,
            MeResponse user) {
    }

    /**
     * Snapshot of the current session: who and which account.
     * Phase 4.6 adds an embedded {@link Brand} the desktop applies as
     * CSS variables for white-labelling.
     */
    public record MeResponse(
            Long userId,
            String username,
            String fullName,
            String role,
            Long accountId,
            String accountName,
            LocalDate subscriptionExpires,
            int daysUntilBlock,
            boolean blocked,
            Brand brand,
            /**
             * Comma-separated module keys this user's account is allowed
             * to see. NULL = all modules visible (legacy/default).
             * The desktop sidebar filters its nav-items against this list.
             */
            String enabledModules,
            /**
             * Phase 4.5: effective resource-action permissions (role
             * defaults + per-user overrides). The client uses this to
             * hide affordances the user can't action. SUPER_ADMIN comes
             * back as the single token {@code "*:*"}.
             */
            java.util.Set<String> permissions) {
    }

    /**
     * White-label brand. All fields nullable — when the account hasn't
     * customised anything the desktop renders the default SavdoPRO look.
     */
    public record Brand(
            String name,
            String colorPrimary,
            String colorSecondary,
            String logoUrl,
            String footerNote) { }
}
