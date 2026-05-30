package uz.barakat.license.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.barakat.license.auth.AdminDtos.CreateUserRequest;
import uz.barakat.license.domain.Account;
import uz.barakat.license.domain.SubscriptionPlan;
import uz.barakat.license.exception.BadRequestException;
import uz.barakat.license.repository.AccountRepository;
import uz.barakat.license.repository.AppUserRepository;

/**
 * Plan limits are real: adding a user is refused once the account has hit
 * the user cap of its subscription tier (TRIAL = 2). The new row is never
 * written when the limit is reached.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceUserLimitTest {

    @Mock private AccountRepository accounts;
    @Mock private AppUserRepository users;
    @Mock private AuditService audit;
    @Mock private RefreshTokenService refreshTokens;

    private AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(accounts, users, audit, refreshTokens);
    }

    @Test
    void createUserRejectedWhenPlanUserLimitReached() {
        Account account = new Account();
        account.setPlan(SubscriptionPlan.TRIAL); // maxUsers = 2
        when(accounts.findById(5L)).thenReturn(Optional.of(account));
        when(users.countByAccountId(5L)).thenReturn(2L); // already at the cap

        assertThatThrownBy(() -> service.createUser(5L,
                new CreateUserRequest("yangikassir", "parol123", "Yangi Kassir", "SHOP_USER")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chegarasi");

        verify(users, never()).save(any());
    }
}
