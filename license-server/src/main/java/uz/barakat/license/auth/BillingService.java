package uz.barakat.license.auth;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.license.domain.Account;
import uz.barakat.license.domain.Payment;
import uz.barakat.license.domain.PaymentStatus;
import uz.barakat.license.domain.SubscriptionPlan;
import uz.barakat.license.exception.BadRequestException;
import uz.barakat.license.exception.NotFoundException;
import uz.barakat.license.repository.AccountRepository;
import uz.barakat.license.repository.PaymentRepository;

/**
 * Subscription billing — provider-agnostic. Start a checkout (a PENDING
 * payment), confirm it from the PSP webhook (PAID → extend the subscription),
 * and list history. The PSP-specific signature check + hosted-checkout URL
 * belong in the webhook adapter; this owns the money→subscription logic so it
 * can be unit-tested without any payment provider.
 */
@Service
public class BillingService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(BillingService.class);

    private final PaymentRepository payments;
    private final AccountRepository accounts;
    private final SmsProvider sms;

    public BillingService(PaymentRepository payments, AccountRepository accounts,
                          SmsProvider sms) {
        this.payments = payments;
        this.accounts = accounts;
        this.sms = sms;
    }

    /**
     * Create a PENDING payment for a paid plan. The controller turns it into a
     * PSP hosted-checkout reference. {@code months} is clamped to >= 1.
     */
    @Transactional
    public Payment startCheckout(Long accountId, SubscriptionPlan plan, int months, String provider) {
        if (plan == SubscriptionPlan.TRIAL) {
            throw new BadRequestException("Sinov rejasini sotib bo'lmaydi");
        }
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> NotFoundException.of("Akkaunt", accountId));
        int m = Math.max(months, 1);
        Payment p = new Payment();
        p.setAccountId(account.getId());
        p.setPlan(plan);
        p.setMonths(m);
        p.setAmountUzs(plan.monthlyPriceUzs() * m);
        p.setStatus(PaymentStatus.PENDING);
        p.setProvider(provider);
        return payments.save(p);
    }

    /**
     * Mark a PENDING payment PAID and extend the subscription. Idempotent —
     * a PSP may deliver the same callback more than once.
     */
    @Transactional
    public Payment confirmPayment(Long paymentId, String externalId) {
        Payment p = payments.findById(paymentId)
                .orElseThrow(() -> NotFoundException.of("To'lov", paymentId));
        if (p.getStatus() == PaymentStatus.PAID) {
            return p;
        }
        p.setStatus(PaymentStatus.PAID);
        p.setExternalId(externalId);
        p.setPaidAt(LocalDateTime.now());
        payments.save(p);

        Account account = accounts.findById(p.getAccountId())
                .orElseThrow(() -> NotFoundException.of("Akkaunt", p.getAccountId()));
        activate(account, p.getPlan(), p.getMonths());
        sendReceipt(account, p);
        return p;
    }

    /**
     * Payment receipt to the merchant's phone — best-effort: a failed SMS
     * must never roll back a confirmed payment, so failures only log.
     * (LoggingSmsProvider makes this a log line until Eskiz is configured.)
     */
    private void sendReceipt(Account account, Payment p) {
        String phone = account.getContactPhone();
        if (phone == null || phone.isBlank()) {
            return;
        }
        try {
            sms.send(phone, "SavdoPRO: to'lov qabul qilindi — "
                    + p.getPlan() + ", " + p.getMonths() + " oy, "
                    + String.format("%,d", p.getAmountUzs()).replace(',', ' ') + " so'm. "
                    + "Obuna " + account.getSubscriptionExpires() + " gacha faol. Rahmat!");
        } catch (Exception ex) {
            log.warn("Payment receipt SMS failed for account {}: {}",
                    account.getId(), ex.toString());
        }
    }

    /**
     * Mark a PENDING payment FAILED (e.g. the customer cancelled at the PSP).
     * Never downgrades an already-PAID payment — a late cancel callback after
     * a successful charge must not revoke a subscription.
     */
    @Transactional
    public void markFailed(Long paymentId) {
        payments.findById(paymentId).ifPresent(p -> {
            if (p.getStatus() != PaymentStatus.PAID) {
                p.setStatus(PaymentStatus.FAILED);
                payments.save(p);
            }
        });
    }

    /**
     * Set the plan, extend the subscription by {@code months} (stacking onto
     * an already-active one rather than truncating it), and clear any block.
     */
    @Transactional
    public void activate(Account account, SubscriptionPlan plan, int months) {
        LocalDate today = LocalDate.now();
        LocalDate current = account.getSubscriptionExpires();
        LocalDate base = (current != null && current.isAfter(today)) ? current : today;
        account.setSubscriptionExpires(base.plusMonths(Math.max(months, 1)));
        account.setPlan(plan);
        account.setBlocked(false);
        accounts.save(account);
    }

    /**
     * Super-admin manual grant (no charge): records a MANUAL PAID payment for
     * the audit trail and activates the plan. Used to comp an account or
     * extend a trial from the admin panel.
     */
    @Transactional
    public void grantSubscription(Long accountId, SubscriptionPlan plan, int months) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> NotFoundException.of("Akkaunt", accountId));
        int m = Math.max(months, 1);
        Payment p = new Payment();
        p.setAccountId(accountId);
        p.setPlan(plan);
        p.setMonths(m);
        p.setAmountUzs(0);
        p.setStatus(PaymentStatus.PAID);
        p.setProvider("MANUAL");
        p.setPaidAt(LocalDateTime.now());
        payments.save(p);
        activate(account, plan, m);
    }

    @Transactional(readOnly = true)
    public List<Payment> history(Long accountId) {
        return payments.findByAccountIdOrderByCreatedAtDescIdDesc(accountId);
    }
}
