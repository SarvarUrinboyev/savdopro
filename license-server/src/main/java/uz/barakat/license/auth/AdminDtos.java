package uz.barakat.license.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** REST payloads used by the super-admin account management endpoints. */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** Snapshot of one account for the admin list view. */
    public record AdminAccountResponse(
            Long id,
            String name,
            String contactPhone,
            String contactNote,
            LocalDate subscriptionExpires,
            int daysUntilBlock,
            boolean blocked,
            boolean expired,
            String plan,
            int userCount,
            LocalDateTime createdAt,
            /** Comma-separated module keys; null = all modules visible. */
            String enabledModules,
            /** How the owner signed up: GOOGLE|TELEGRAM|FACEBOOK|X|PASSWORD. */
            String signupProvider) {
    }

    /** Body of {@code PATCH /api/admin/accounts/{id}/modules}. */
    public record ModulesRequest(
            /** Comma-separated module keys, or null/empty to enable all. */
            String enabledModules) {
    }

    public record AdminUserResponse(
            Long id,
            String username,
            String fullName,
            String role,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt,
            String permissions) {
    }

    public record AccountDetailResponse(
            AdminAccountResponse account,
            List<AdminUserResponse> users) {
    }

    /** Creates a new account together with its first (owner) user. */
    public record CreateAccountRequest(
            @NotBlank(message = "Akkaunt nomi kiritilishi shart") String name,
            String contactPhone,
            String contactNote,
            LocalDate subscriptionExpires,
            @NotBlank(message = "Login kiritilishi shart")
            @Size(min = 3, max = 80) String ownerUsername,
            @NotBlank(message = "Parol kiritilishi shart")
            @StrongPassword String ownerPassword,
            String ownerFullName) {
    }

    public record UpdateAccountRequest(
            @NotBlank(message = "Akkaunt nomi kiritilishi shart") String name,
            String contactPhone,
            String contactNote,
            LocalDate subscriptionExpires,
            // Phase 4.6 white-label brand. All optional; missing fields
            // leave the previous value untouched.
            String brandName,
            String brandColorPrimary,
            String brandColorSecondary,
            String brandLogoUrl,
            String brandFooterNote) {
    }

    /** Body of {@code PATCH /api/admin/accounts/{id}/block}. */
    public record BlockRequest(boolean blocked) {
    }

    /** Body of {@code PATCH /api/admin/accounts/{id}/require-mfa}. */
    public record RequireMfaRequest(boolean requireMfa) {
    }

    /** Body of password reset / new user creation. */
    public record SetPasswordRequest(
            @NotBlank(message = "Parol kiritilishi shart")
            @StrongPassword String password) {
    }

    /** Super-admin manual subscription grant: set a plan for N months. */
    public record GrantSubscriptionRequest(
            @NotBlank(message = "Reja tanlanishi shart") String plan,
            Integer months) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 80) String username,
            @NotBlank @StrongPassword String password,
            String fullName,
            String role) {
    }

    /**
     * Body of {@code PATCH /api/admin/users/{id}/permissions}. Send the
     * full CSV of "RESOURCE:ACTION" tokens (or null/blank to clear the
     * override and fall back to role defaults). Unknown tokens are
     * rejected with 400 so a typo never silently grants nothing.
     */
    public record SetPermissionsRequest(String permissions) { }
}
