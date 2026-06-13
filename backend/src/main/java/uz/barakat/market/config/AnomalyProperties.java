package uz.barakat.market.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI anomaly-control thresholds, bound from the {@code anomaly.*} keys.
 *
 * <p>Detection is deterministic — these are the only tunables. The
 * canonical constructor backfills sane defaults for any missing or
 * non-sensical value, so a partially-overridden {@code application-local.properties}
 * can never silently zero a threshold (which would make a detector fire
 * constantly, or never fire). Mirrors {@code TelegramProperties}.
 */
@ConfigurationProperties(prefix = "anomaly")
public record AnomalyProperties(
        boolean enabled,
        double refundRatePct,
        double refundRateCriticalPct,
        int refundBurstPerHour,
        BigDecimal largeRefundMinUsd,
        int refundLateDays,
        int nightStartHour,
        int nightEndHour,
        double nightSpikeMultiplier,
        int nightSpikeMinCount,
        BigDecimal belowCostMinDailyUsd,
        double cashierRefundOutlierMult,
        int cashierMinReceipts,
        int baselineDays,
        int minBaselineDays) {

    public AnomalyProperties {
        refundRatePct          = positive(refundRatePct, 15);
        refundRateCriticalPct  = positive(refundRateCriticalPct, 30);
        refundBurstPerHour     = positive(refundBurstPerHour, 4);
        largeRefundMinUsd      = positive(largeRefundMinUsd, new BigDecimal("50"));
        refundLateDays         = positive(refundLateDays, 3);
        nightStartHour         = hour(nightStartHour, 23);
        nightEndHour           = hour(nightEndHour, 6);
        nightSpikeMultiplier   = positive(nightSpikeMultiplier, 3.0);
        nightSpikeMinCount     = positive(nightSpikeMinCount, 3);
        belowCostMinDailyUsd   = positive(belowCostMinDailyUsd, new BigDecimal("5"));
        cashierRefundOutlierMult = positive(cashierRefundOutlierMult, 2.5);
        cashierMinReceipts     = positive(cashierMinReceipts, 5);
        baselineDays           = positive(baselineDays, 28);
        minBaselineDays        = positive(minBaselineDays, 7);
    }

    private static double positive(double v, double dflt) {
        return v > 0 ? v : dflt;
    }

    private static int positive(int v, int dflt) {
        return v > 0 ? v : dflt;
    }

    private static BigDecimal positive(BigDecimal v, BigDecimal dflt) {
        return v != null && v.signum() > 0 ? v : dflt;
    }

    /** Hour must be 1..23; anything else (incl. a missing 0) falls back to the default. */
    private static int hour(int v, int dflt) {
        return (v >= 1 && v <= 23) ? v : dflt;
    }
}
