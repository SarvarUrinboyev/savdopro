package uz.barakat.license.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One row per super-admin write action. Append-only — the panel queries
 * this table in reverse chronological order. {@code actorName} and
 * {@code targetLabel} are denormalised so the audit row stays meaningful
 * even after the user or account it references is deleted.
 */
@Entity
@Table(name = "admin_audit_log")
@Getter
@Setter
public class AdminAuditEntry extends BaseEntity {

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "actor_name", nullable = false, length = 80)
    private String actorName;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_label", length = 180)
    private String targetLabel;

    @Column(length = 500)
    private String detail;

    @Column(name = "client_ip", length = 64)
    private String clientIp;
}
