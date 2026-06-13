package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/**
 * One persisted anomaly flagged by the AI anomaly-control engine
 * ({@code AnomalyDetectionService} + {@code AnomalyMonitorService}).
 *
 * <p>Append-only and shop-scoped, exactly like {@link AuditEntry}. The
 * detector recomputes the same anomalies on every scan, so
 * {@code (shop_id, dedupe_key)} is UNIQUE: a repeated scan of the same
 * day never inserts a duplicate row and never re-pushes the same alert to
 * Telegram. {@code occurredOn} is the business day the anomaly is about
 * (independent of when the scan ran), so history and the banner group by
 * day cleanly.
 */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Filter(name = "accountFilter", condition = "shop_id IN (:shopIds)")
@Entity
@Table(name = "anomaly_alerts")
@Getter
@Setter
public class AnomalyAlert extends TenantScopedEntity {

    /** "info" / "warn" / "critical". */
    @Column(nullable = false, length = 10)
    private String severity;

    /** Machine tag of the detector, e.g. "till-negative". */
    @Column(nullable = false, length = 40)
    private String code;

    /** Stable uniqueness handle: code + occurredOn + subject (saleId/cashier/hour). */
    @Column(name = "dedupe_key", nullable = false, length = 120)
    private String dedupeKey;

    /** Business day this anomaly is about. */
    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    /** Deterministic, human-readable Uzbek description. */
    @Column(nullable = false, length = 500)
    private String message;

    /** Optional structured payload (small JSON) for the detail view. */
    @Column(name = "detail_json", length = 2000)
    private String detailJson;

    @Column(nullable = false)
    private boolean acknowledged = false;

    @Column(name = "acknowledged_by", length = 120)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    /** True once a warn/critical alert has been pushed to Telegram (no re-send). */
    @Column(name = "telegram_sent", nullable = false)
    private boolean telegramSent = false;
}
