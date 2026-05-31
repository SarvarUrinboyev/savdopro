package uz.barakat.license.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

/**
 * Default {@link SmsProvider} that just logs the message instead of
 * actually delivering it. Useful for local dev (you can copy the OTP
 * straight out of the server log) and for CI / unit tests. Swapped
 * automatically when another {@code SmsProvider} bean is registered —
 * we use {@link ConditionalOnMissingBean} so a production
 * {@code @Component} class wins without needing extra wiring.
 */
@Configuration
class LoggingSmsProviderConfig {

    // Real gateway when sms.provider=eskiz; declared before the logging bean so
    // the @ConditionalOnMissingBean below sees it and steps aside.
    @Bean
    @ConditionalOnProperty(name = "sms.provider", havingValue = "eskiz")
    public SmsProvider eskizSmsProvider(
            @Value("${sms.eskiz.email:}") String email,
            @Value("${sms.eskiz.password:}") String password,
            @Value("${sms.eskiz.from:4546}") String from,
            @Value("${sms.eskiz.base-url:https://notify.eskiz.uz}") String baseUrl) {
        return new EskizSmsProvider(email, password, from, baseUrl);
    }

    @Bean
    @ConditionalOnMissingBean(SmsProvider.class)
    public SmsProvider loggingSmsProvider() {
        return new LoggingSmsProvider();
    }
}

class LoggingSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsProvider.class);

    @Override
    public boolean send(String phone, String body) {
        log.info("[SMS-LOG] -> phone={} body={}", phone, body);
        return true;
    }
}
