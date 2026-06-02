package uz.barakat.license.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.barakat.license.domain.AppUser;
import uz.barakat.license.exception.BadRequestException;
import uz.barakat.license.repository.AccountRepository;
import uz.barakat.license.repository.AppUserRepository;

/**
 * Forgot-password reset: a valid SMS code re-hashes the password and kills
 * every existing session; a wrong code changes nothing.
 */
@ExtendWith(MockitoExtension.class)
class AuthServicePasswordResetTest {

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

    @BeforeEach
    void setUp() {
        service = new AuthService(users, accounts, jwt, refreshTokens, totp,
                telegramVerifier, otp, sms, permissions, alerter);
    }

    @Test
    void validCodeSetsNewPasswordAndRevokesSessions() {
        when(otp.verify(any(), eq("123456"))).thenReturn(true);
        AppUser user = new AppUser();
        user.setId(9L);
        user.setPasswordHash("OLD-HASH");
        when(users.findByPhone(any())).thenReturn(Optional.of(user));
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resetPassword("+998901112233", "123456", "yangiparol");

        assertThat(user.getPasswordHash()).isNotBlank().isNotEqualTo("OLD-HASH");
        verify(refreshTokens).revokeAllForUser(9L);
    }

    @Test
    void wrongCodeThrowsAndChangesNothing() {
        when(otp.verify(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.resetPassword("+998901112233", "000000", "yangiparol"))
                .isInstanceOf(BadRequestException.class);

        verify(users, never()).save(any());
        verify(refreshTokens, never()).revokeAllForUser(anyLong());
    }
}
