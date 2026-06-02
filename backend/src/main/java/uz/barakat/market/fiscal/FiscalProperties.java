package uz.barakat.market.fiscal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fiscalization (OFD — fiskal ma'lumotlar operatori) configuration, bound
 * from {@code fiscal.*}. In Uzbekistan every retail sale must be registered
 * with the tax authority through a licensed OFD, which returns a fiscal sign
 * + QR for the receipt. This is disabled until a real provider + credentials
 * are configured; while disabled {@link FiscalizationService} is a no-op, so
 * the app runs identically without an OFD contract.
 *
 * <p>Real values go in {@code application-local.properties} or env vars.</p>
 */
@ConfigurationProperties(prefix = "fiscal")
public record FiscalProperties(
        boolean enabled,
        /** Provider key, e.g. "soliq", "didox", "iiko" — selects the adapter. */
        String provider,
        String apiUrl,
        String apiKey,
        /** The registered cash register / terminal (ZNM/INN) id. */
        String terminalId) {

    public boolean isUsable() {
        return enabled
                && notBlank(apiUrl) && notBlank(apiKey) && !apiKey.startsWith("PUT-");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
