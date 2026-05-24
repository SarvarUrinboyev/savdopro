package uz.barakat.license.auth;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Login / session DTOs grouped in one file to avoid noise. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Login kiritilishi shart") String username,
            @NotBlank(message = "Parol kiritilishi shart") String password) {
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

    /** Snapshot of the current session: who and which account. */
    public record MeResponse(
            Long userId,
            String username,
            String fullName,
            String role,
            Long accountId,
            String accountName,
            LocalDate subscriptionExpires,
            int daysUntilBlock,
            boolean blocked) {
    }
}
