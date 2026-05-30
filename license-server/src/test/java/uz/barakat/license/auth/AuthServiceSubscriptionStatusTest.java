package uz.barakat.license.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.barakat.license.auth.AuthDtos.SubscriptionStatusResponse;
import uz.barakat.license.domain.Account;
import uz.barakat.license.domain.AppUser;
import uz.barakat.license.domain.SubscriptionPlan;
import uz.barakat.license.repository.AccountRepository;
import uz.barakat.license.repository.AppUserRepository;

/**
 * The billing-status snapshot reports the account's plan, its limits, the live
 * user count, and a non-negative trial countdown.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceSubscriptionStatusTest {

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
    void statusReportsPlanLimitsAndTrialCountdown() {
        AppUser user = new AppUser();
        user.setId(3L);
        user.setAccountId(7L);
        Account account = new Account();
        account.setPlan(SubscriptionPlan.TRIAL);
        account.setSubscriptionExpires(LocalDate.now().plusDays(10));
        when(users.findById(3L)).thenReturn(Optional.of(user));
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(users.countByAccountId(7L)).thenReturn(1L);

        SubscriptionStatusResponse s = service.subscriptionStatus(3L);

        assertThat(s.plan()).isEqualTo("TRIAL");
        assertThat(s.maxUsers()).isEqualTo(2);
        assertThat(s.currentUsers()).isEqualTo(1L);
        assertThat(s.maxShops()).isEqualTo(1);
        assertThat(s.expired()).isFalse();
        assertThat(s.blocked()).isFalse();
        assertThat(s.daysRemaining()).isGreaterThanOrEqualTo(0);
        assertThat(s.monthlyPriceUzs()).isZero(); // TRIAL is free
    }
}
