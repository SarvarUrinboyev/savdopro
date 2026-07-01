package uz.barakat.license.auth;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.license.domain.Account;
import uz.barakat.license.domain.DunningLog;
import uz.barakat.license.domain.SubscriptionPlan;
import uz.barakat.license.repository.AccountRepository;
import uz.barakat.license.repository.DunningLogRepository;

/**
 * Subscription dunning: a daily sweep that reminds merchants BEFORE their
 * subscription (or trial) lapses and tells them exactly when the shop
 * backend's write-block lands, instead of letting the POS silently brick.
 *
 * <p>Milestones per expiry cycle: D-7, D-3, D-1 (days left), D0 (last day)
 * and GRACE_END (the backend's grace window is over — writes now refuse).
 * The {@code dunning_log} unique key makes each milestone fire at most once
 * per (account, expiry date); a renewal moves the expiry date and re-arms
 * the whole cycle.
 *
 * <p>Transport is the pluggable {@link SmsProvider} (LoggingSmsProvider by
 * default — messages appear in the log until Eskiz creds are configured).
 * A row is written even when the SMS transport fails so a flaky gateway
 * can never cause repeat spam; the outcome is kept in {@code smsSent}.
 */
@Service
public class DunningService {

    private static final Logger log = LoggerFactory.getLogger(DunningService.class);

    private final AccountRepository accounts;
    private final DunningLogRepository dunningLog;
    private final SmsProvider sms;
    private final boolean enabled;
    private final int graceDays;

    public DunningService(AccountRepository accounts,
                          DunningLogRepository dunningLog,
                          SmsProvider sms,
                          @Value("${dunning.enabled:true}") boolean enabled,
                          @Value("${dunning.grace-days:3}") int graceDays) {
        this.accounts = accounts;
        this.dunningLog = dunningLog;
        this.sms = sms;
        this.enabled = enabled;
        this.graceDays = Math.max(0, graceDays);
    }

    /** 04:00 UTC = 09:00 Toshkent — reminders land at the start of the workday. */
    @Scheduled(cron = "${dunning.cron:0 0 4 * * *}")
    public void dailySweep() {
        if (!enabled) {
            return;
        }
        try {
            int sent = sweep(LocalDate.now());
            if (sent > 0) {
                log.info("Dunning sweep done: {} reminder(s) processed.", sent);
            }
        } catch (Exception ex) {
            // A dunning failure must never take the scheduler thread down.
            log.error("Dunning sweep failed: {}", ex.toString(), ex);
        }
    }

    /** Visible for tests; returns how many milestone reminders were processed. */
    @Transactional
    public int sweep(LocalDate today) {
        int processed = 0;
        for (Account a : accounts.findAll()) {
            if (a.isBlocked() || a.getSubscriptionExpires() == null) {
                continue;
            }
            LocalDate expiry = a.getSubscriptionExpires();
            String milestone = milestoneFor(today, expiry);
            if (milestone == null
                    || dunningLog.existsByAccountIdAndMilestoneAndExpiryDate(
                            a.getId(), milestone, expiry)) {
                continue;
            }
            boolean smsOk = false;
            String phone = a.getContactPhone();
            String body = message(milestone, a.getPlan(), expiry);
            if (phone != null && !phone.isBlank()) {
                try {
                    smsOk = sms.send(phone, body);
                } catch (Exception ex) {
                    log.warn("Dunning SMS failed for account {}: {}", a.getId(), ex.toString());
                }
            } else {
                log.info("Dunning {} for account {} (no phone on file): {}",
                        milestone, a.getId(), body);
            }
            DunningLog row = new DunningLog();
            row.setAccountId(a.getId());
            row.setMilestone(milestone);
            row.setExpiryDate(expiry);
            row.setSmsSent(smsOk);
            dunningLog.save(row);
            processed++;
        }
        return processed;
    }

    /** Maps "days until expiry" onto a milestone, or null when today is quiet. */
    String milestoneFor(LocalDate today, LocalDate expiry) {
        long daysLeft = ChronoUnit.DAYS.between(today, expiry);
        if (daysLeft == 7) return "D-7";
        if (daysLeft == 3) return "D-3";
        if (daysLeft == 1) return "D-1";
        if (daysLeft == 0) return "D0";
        if (daysLeft == -graceDays) return "GRACE_END";
        return null;
    }

    private String message(String milestone, SubscriptionPlan plan, LocalDate expiry) {
        String what = (plan == SubscriptionPlan.TRIAL) ? "Sinov muddati" : "Obunangiz";
        return switch (milestone) {
            case "D-7" -> "SavdoPRO: " + what + " 7 kundan keyin (" + expiry
                    + ") tugaydi. Tarifni Billing sahifasidan yangilang.";
            case "D-3" -> "SavdoPRO: " + what + " 3 kundan keyin tugaydi. "
                    + "Uzilishsiz ishlash uchun to'lovni amalga oshiring.";
            case "D-1" -> "SavdoPRO: " + what + " ERTAGA tugaydi. "
                    + "Bugun to'lasangiz, do'kon uzilishsiz ishlaydi.";
            case "D0" -> "SavdoPRO: " + what + " BUGUN tugaydi. To'lovdan so'ng "
                    + "hech narsa o'chmaydi — barcha ma'lumot saqlanadi.";
            default -> "SavdoPRO: to'lov qilinmagani uchun bugundan do'konda yangi "
                    + "savdo kiritish to'xtatildi (ko'rish ochiq). To'lov qilsangiz "
                    + "darhol tiklanadi.";
        };
    }
}
