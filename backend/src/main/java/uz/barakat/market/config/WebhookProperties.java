package uz.barakat.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbound-webhook delivery tuning, bound from {@code webhook.*}.
 * Canonical constructor backfills safe defaults so a partial override can't
 * disable delivery. Mirrors {@code AnomalyProperties}.
 */
@ConfigurationProperties(prefix = "webhook")
public record WebhookProperties(
        boolean enabled,
        int maxAttempts,
        int timeoutSeconds,
        int batchSize,
        int backoffBaseSeconds) {

    public WebhookProperties {
        maxAttempts       = positive(maxAttempts, 6);
        timeoutSeconds    = positive(timeoutSeconds, 10);
        batchSize         = positive(batchSize, 50);
        backoffBaseSeconds = positive(backoffBaseSeconds, 30);
    }

    private static int positive(int v, int dflt) {
        return v > 0 ? v : dflt;
    }
}
