package uz.barakat.market.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Transactional email (invoices, receipts, owner notifications). A thin,
 * config-gated wrapper over Spring's {@link JavaMailSender}: when SMTP is not
 * configured ({@code app.email.enabled=false} or no {@code spring.mail.host})
 * every send is a logged no-op, so the app behaves identically with or
 * without email credentials. Never throws — a failed email must not break
 * the flow that triggered it.
 *
 * <p>To activate, set in {@code application-local.properties} (or env):
 * {@code app.email.enabled=true}, {@code spring.mail.host/port/username/password}
 * and {@code app.email.from}.</p>
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender; // null when SMTP not auto-configured
    private final boolean enabled;
    private final String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${app.email.enabled:false}") boolean enabled,
                        @Value("${app.email.from:no-reply@savdopro.uz}") String from) {
        this.mailSender = mailSender.getIfAvailable();
        this.enabled = enabled;
        this.from = from;
    }

    /** True only when email is switched on and a mail sender is configured. */
    public boolean isUsable() {
        return enabled && mailSender != null;
    }

    /** Sends a plain-text email. Returns true if the gateway accepted it. */
    public boolean send(String to, String subject, String body) {
        if (!isUsable()) {
            log.info("Email disabled — would send to {}: {}", to, subject);
            return false;
        }
        if (to == null || to.isBlank()) {
            return false;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Email sent to {}", to);
            return true;
        } catch (Exception ex) {
            log.warn("Email to {} failed: {}", to, ex.toString());
            return false;
        }
    }
}
