package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/** A recorded write operation against a shop's data (local audit trail). */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Filter(name = "accountFilter", condition = "shop_id IN (:shopIds)")
@Entity
@Table(name = "audit_log")
@Getter
@Setter
public class AuditEntry extends TenantScopedEntity {

    @Column(length = 120)
    private String actor;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 300)
    private String path;

    @Column(nullable = false)
    private int status;

    @Column(name = "client_ip", length = 64)
    private String clientIp;
}
