package uz.barakat.license.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import uz.barakat.license.auth.AuthDtos.LoginRequest;
import uz.barakat.license.domain.Account;
import uz.barakat.license.domain.AppUser;
import uz.barakat.license.domain.UserRole;
import uz.barakat.license.exception.BadRequestException;
import uz.barakat.license.repository.AccountRepository;
import uz.barakat.license.repository.AppUserRepository;

/**
 * A user with 2FA can log in with a one-time backup code when the authenticator
 * code fails; the code is consumed so it cannot be reused.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceBackupCodeTest {

    @Mock private AppUserRepository users;
    @Mock private AccountRepository accounts;
    @Mock private JwtService jwt;
    @Mock private RefreshTokenService refreshTokens;
    @Mock private TotpService totp;
    @Mock private TelegramOAuthVerifier telegramVerifier;
    @Mock private OtpService otp;
    @Mock private SmsProvider sms;
    @Mock private PermissionService permissions;
    @Mock private SuspiciousLoginAlerter alerter;

    private AuthService service;
    private AppUser user;

    private static String hash(String code) {
        try {
            String norm = code.trim().toUpperCase().replace("-", "");
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(norm.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeEach
    void setUp() {
        service = new AuthService(users, accounts, jwt, refreshTokens, totp,
                telegramVerifier, otp, sms, permissions, alerter);

        user = new AppUser();
        user.setId(5L);
        user.setUsername("owner");
        user.setRole(UserRole.ACCOUNT_OWNER);
        user.setAccountId(7L);
        user.setPasswordHash(new BCryptPasswordEncoder(12).encode("pass123"));
        user.setTotpEnabled(true);
        user.setTotpSecret("SECRET");
        // Two stored backup-code hashes; we'll log in with the first.
        user.setTotpBackupCodes(hash("ABCDE-FGHIJ") + "\n" + hash("KLMNP-QRSTU"));

        Account account = new Account();
        account.setId(7L);
        account.setName("Shop");
        account.setBlocked(false);
        account.setSubscriptionExpires(LocalDate.now().plusDays(30));

        when(users.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.of(user));
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(totp.verify(any(), any())).thenReturn(false);   // authenticator code never matches
        when(permissions.effective(any())).thenReturn(Set.of("*:*"));
        when(jwt.issue(any())).thenReturn("access-jwt");
        when(jwt.accessTtlSeconds()).thenReturn(3600L);
        when(refreshTokens.issue(anyLong(), anyLong(), any()))
                .thenReturn(new RefreshTokenService.Issued("refresh-tok", LocalDateTime.now().plusDays(7)));
    }

    @Test
    void backupCodeLogsInThenIsConsumed() {
        // First login with a valid backup code succeeds.
        var resp = service.login(new LoginRequest("owner", "pass123", "ABCDE-FGHIJ"), "1.2.3.4");
        assertThat(resp.token()).isEqualTo("access-jwt");
        // The used code is gone; the other remains.
        assertThat(user.getTotpBackupCodes()).doesNotContain(hash("ABCDE-FGHIJ"));
        assertThat(user.getTotpBackupCodes()).contains(hash("KLMNP-QRSTU"));

        // Reusing the same code now fails (consumed).
        assertThatThrownBy(() -> service.login(
                new LoginRequest("owner", "pass123", "ABCDE-FGHIJ"), "1.2.3.4"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void wrongCodeIsRejected() {
        assertThatThrownBy(() -> service.login(
                new LoginRequest("owner", "pass123", "ZZZZZ-ZZZZZ"), "1.2.3.4"))
                .isInstanceOf(BadRequestException.class);
    }
}
