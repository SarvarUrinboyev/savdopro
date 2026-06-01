package uz.barakat.market.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SMS gateway configuration, bound from {@code sms.*}. The default
 * provider is Eskiz.uz (the de-facto Uzbekistan SMS gateway). When
 * {@link #isUsable()} is false every send is a logged no-op, so the
 * rest of the app behaves identically with or without SMS credentials.
 *
 * <p>Set the real values in {@code application-local.properties} (server)
 * or via environment variables — never commit credentials.</p>
 */
@ConfigurationProperties(prefix = "sms")
public record SmsProperties(
        boolean enabled,
        String baseUrl,
        String email,
        String password,
        String from) {

    public SmsProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://notify.eskiz.uz/api";
        }
        if (from == null || from.isBlank()) {
            from = "4546";
        }
    }

    /** True only when SMS is switched on and real credentials are present. */
    public boolean isUsable() {
        return enabled
                && email != null && !email.isBlank() && !email.startsWith("PUT-")
                && password != null && !password.isBlank() && !password.startsWith("PUT-");
    }
}
