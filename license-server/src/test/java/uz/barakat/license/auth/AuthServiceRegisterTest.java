package uz.barakat.license.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.barakat.license.auth.AuthDtos.RegisterRequest;
import uz.barakat.license.domain.Account;
import uz.barakat.license.domain.AppUser;
import uz.barakat.license.domain.UserRole;
import uz.barakat.license.exception.BadRequestException;
import uz.barakat.license.repository.AccountRepository;
import uz.barakat.license.repository.AppUserRepository;

/**
 * Self-service signup: a new merchant gets a trial account, an ACCOUNT_OWNER
 * user with a BCrypt-hashed password, and an immediate session. A taken
 * username is rejected before anything is written.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRegisterTest {

    @Mock private AppUserRepository users;
    @Mock private AccountRepository accounts;
    @Mock private JwtService jwt;
    @Mock private RefreshTokenService refreshTokens;
    @Mock private TotpService totp;
    @Mock private TelegramOAuthVerifier telegramVerifier;
    @Mock private OtpService otp;
    @Mock private SmsProvider sms;
    @Mock private PermissionService permissions;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, accounts, jwt, refreshTokens, totp,
                telegramVerifier, otp, sms, permissions);
    }

    @Test
    void signupCreatesTrialOwnerAndReturnsSession() {
        when(users.existsByUsernameIgnoreCase("yangidokon")).thenReturn(false);
        when(accounts.save(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setId(7L);
            return a;
        });
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwt.issue(any(AppUser.class))).thenReturn("jwt-token");
        when(jwt.accessTtlSeconds()).thenReturn(3600L);
        when(refreshTokens.issue(any(), any(), any()))
                .thenReturn(new RefreshTokenService.Issued("refresh-plain",
                        LocalDateTime.now().plusDays(7)));
        when(permissions.effective(any(AppUser.class))).thenReturn(Set.of());

        var resp = service.register(
                new RegisterRequest("Yangi Do'kon", "Ali Valiyev",
                        "YangiDokon", "parol123", "+998901112233"),
                "1.2.3.4");

        assertThat(resp.token()).isEqualTo("jwt-token");
        assertThat(resp.refreshToken()).isEqualTo("refresh-plain");

        ArgumentCaptor<Account> acc = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(acc.capture());
        assertThat(acc.getValue().getName()).isEqualTo("Yangi Do'kon");
        assertThat(acc.getValue().getSubscriptionExpires())
                .isEqualTo(LocalDate.now().plusDays(AuthService.TRIAL_DAYS));
        assertThat(acc.getValue().isBlocked()).isFalse();

        ArgumentCaptor<AppUser> usr = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(usr.capture());
        assertThat(usr.getValue().getUsername()).isEqualTo("yangidokon"); // normalised lower-case
        assertThat(usr.getValue().getRole()).isEqualTo(UserRole.ACCOUNT_OWNER);
        assertThat(usr.getValue().getAccountId()).isEqualTo(7L);
        assertThat(usr.getValue().getPasswordHash())
                .isNotBlank()
                .isNotEqualTo("parol123"); // hashed, never stored in clear
    }

    @Test
    void signupRejectsTakenUsernameBeforeWriting() {
        when(users.existsByUsernameIgnoreCase("band")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterRequest("X", "Y", "Band", "parol123", null), "1.2.3.4"))
                .isInstanceOf(BadRequestException.class);

        verify(accounts, never()).save(any());
        verify(users, never()).save(any());
    }
}
