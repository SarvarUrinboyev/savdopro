package uz.barakat.license.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * One row per dunning reminder actually processed — the unique
 * (account, milestone, expiryDate) triple is what makes the daily
 * {@code DunningService} sweep idempotent. A renewal moves
 * {@code expiryDate}, which re-arms every milestone for the next cycle.
 *
 * <p>{@code smsSent} records the transport outcome for the ops trail;
 * the row exists either way so a flaky gateway can't cause repeat spam.
 */
@Entity
@Table(name = "dunning_log")
@Getter
@Setter
public class DunningLog extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** D-7, D-3, D-1, D0 (last day) or GRACE_END (writes lock tomorrow). */
    @Column(name = "milestone", nullable = false, length = 16)
    private String milestone;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "sms_sent", nullable = false)
    private boolean smsSent;
}
