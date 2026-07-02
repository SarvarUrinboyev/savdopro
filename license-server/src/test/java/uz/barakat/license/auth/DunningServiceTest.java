package uz.barakat.license.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.barakat.license.domain.Account;
import uz.barakat.license.domain.DunningLog;
import uz.barakat.license.domain.SubscriptionPlan;
import uz.barakat.license.repository.AccountRepository;
import uz.barakat.license.repository.DunningLogRepository;

/**
 * Dunning sweep semantics: milestones fire exactly on their day, are
 * idempotent per (account, milestone, expiry), skip blocked accounts, and a
 * row is recorded even when SMS transport fails (no repeat spam).
 */
@ExtendWith(MockitoExtension.class)
class DunningServiceTest {

    @Mock private AccountRepository accounts;
    @Mock private DunningLogRepository dunningLog;
    @Mock private SmsProvider sms;

    private DunningService dunning;

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 2);

    @BeforeEach
    void setUp() {
        dunning = new DunningService(accounts, dunningLog, sms, true, 3);
    }

    private Account account(long id, LocalDate expires, String phone) {
        Account a = new Account();
        a.setId(id);
        a.setSubscriptionExpires(expires);
        a.setContactPhone(phone);
        a.setPlan(SubscriptionPlan.BASIC);
        return a;
    }

    @Test
    void milestoneMapping() {
        assertThat(dunning.milestoneFor(TODAY, TODAY.plusDays(7))).isEqualTo("D-7");
        assertThat(dunning.milestoneFor(TODAY, TODAY.plusDays(3))).isEqualTo("D-3");
        assertThat(dunning.milestoneFor(TODAY, TODAY.plusDays(1))).isEqualTo("D-1");
        assertThat(dunning.milestoneFor(TODAY, TODAY)).isEqualTo("D0");
        assertThat(dunning.milestoneFor(TODAY, TODAY.minusDays(3))).isEqualTo("GRACE_END");
        assertThat(dunning.milestoneFor(TODAY, TODAY.plusDays(5))).isNull();
        assertThat(dunning.milestoneFor(TODAY, TODAY.minusDays(1))).isNull();
    }

    @Test
    void sendsSmsAndRecordsLogOnMilestoneDay() {
        Account a = account(1L, TODAY.plusDays(3), "+998901234567");
        when(accounts.findAll()).thenReturn(List.of(a));
        when(dunningLog.existsByAccountIdAndMilestoneAndExpiryDate(1L, "D-3", a.getSubscriptionExpires()))
                .thenReturn(false);
        when(sms.send(eq("+998901234567"), contains("3 kundan keyin"))).thenReturn(true);

        int processed = dunning.sweep(TODAY);

        assertThat(processed).isEqualTo(1);
        verify(dunningLog).save(any(DunningLog.class));
    }

    @Test
    void alreadySentMilestoneIsNotRepeated() {
        Account a = account(1L, TODAY.plusDays(3), "+998901234567");
        when(accounts.findAll()).thenReturn(List.of(a));
        when(dunningLog.existsByAccountIdAndMilestoneAndExpiryDate(1L, "D-3", a.getSubscriptionExpires()))
                .thenReturn(true);

        int processed = dunning.sweep(TODAY);

        assertThat(processed).isZero();
        verify(sms, never()).send(anyString(), anyString());
        verify(dunningLog, never()).save(any());
    }

    @Test
    void blockedAndQuietDayAccountsAreSkipped() {
        Account blocked = account(1L, TODAY.plusDays(3), "+998901");
        blocked.setBlocked(true);
        Account quiet = account(2L, TODAY.plusDays(5), "+998902"); // no milestone at D-5
        when(accounts.findAll()).thenReturn(List.of(blocked, quiet));

        assertThat(dunning.sweep(TODAY)).isZero();
        verify(sms, never()).send(anyString(), anyString());
    }

    @Test
    void smsFailureStillRecordsRow_soNoRepeatSpam() {
        Account a = account(1L, TODAY, "+998901234567"); // D0
        when(accounts.findAll()).thenReturn(List.of(a));
        when(dunningLog.existsByAccountIdAndMilestoneAndExpiryDate(1L, "D0", TODAY))
                .thenReturn(false);
        when(sms.send(anyString(), anyString())).thenReturn(false);

        assertThat(dunning.sweep(TODAY)).isEqualTo(1);
        verify(dunningLog).save(any(DunningLog.class));
    }
}
