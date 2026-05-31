package uz.barakat.license.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.barakat.license.domain.Account;
import uz.barakat.license.domain.Payment;
import uz.barakat.license.domain.PaymentStatus;
import uz.barakat.license.domain.SubscriptionPlan;
import uz.barakat.license.exception.BadRequestException;
import uz.barakat.license.repository.AccountRepository;
import uz.barakat.license.repository.PaymentRepository;

/**
 * The money→subscription logic: activating a plan extends the subscription
 * (from today when lapsed, stacking when still active), sets the plan, clears
 * any block; confirming a payment is idempotent.
 */
@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock private PaymentRepository payments;
    @Mock private AccountRepository accounts;
    @InjectMocks private BillingService billing;

    @Test
    void activateExtendsFromTodayWhenLapsedAndClearsBlock() {
        Account acc = new Account();
        acc.setPlan(SubscriptionPlan.TRIAL);
        acc.setSubscriptionExpires(LocalDate.now().minusDays(5)); // lapsed
        acc.setBlocked(true);
        when(accounts.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        billing.activate(acc, SubscriptionPlan.BASIC, 2);

        assertThat(acc.getPlan()).isEqualTo(SubscriptionPlan.BASIC);
        assertThat(acc.getSubscriptionExpires()).isEqualTo(LocalDate.now().plusMonths(2));
        assertThat(acc.isBlocked()).isFalse();
    }

    @Test
    void activateStacksOntoAnActiveSubscription() {
        LocalDate future = LocalDate.now().plusDays(10);
        Account acc = new Account();
        acc.setSubscriptionExpires(future);
        when(accounts.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        billing.activate(acc, SubscriptionPlan.STANDARD, 1);

        assertThat(acc.getSubscriptionExpires()).isEqualTo(future.plusMonths(1));
    }

    @Test
    void confirmPaymentMarksPaidAndActivates() {
        Payment p = new Payment();
        p.setAccountId(7L);
        p.setPlan(SubscriptionPlan.BASIC);
        p.setMonths(1);
        p.setStatus(PaymentStatus.PENDING);
        Account acc = new Account();
        acc.setSubscriptionExpires(null);
        when(payments.findById(3L)).thenReturn(Optional.of(p));
        when(payments.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(accounts.findById(7L)).thenReturn(Optional.of(acc));
        when(accounts.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        billing.confirmPayment(3L, "psp-tx-1");

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(p.getExternalId()).isEqualTo("psp-tx-1");
        assertThat(acc.getPlan()).isEqualTo(SubscriptionPlan.BASIC);
        assertThat(acc.getSubscriptionExpires()).isEqualTo(LocalDate.now().plusMonths(1));
    }

    @Test
    void confirmPaymentIsIdempotent() {
        Payment p = new Payment();
        p.setStatus(PaymentStatus.PAID);
        when(payments.findById(3L)).thenReturn(Optional.of(p));

        billing.confirmPayment(3L, "again");

        // No re-activation when the payment is already PAID.
        verify(accounts, never()).save(any());
    }

    @Test
    void startCheckoutRejectsTrialPlan() {
        assertThatThrownBy(() -> billing.startCheckout(7L, SubscriptionPlan.TRIAL, 1, "MANUAL"))
                .isInstanceOf(BadRequestException.class);
    }
}
