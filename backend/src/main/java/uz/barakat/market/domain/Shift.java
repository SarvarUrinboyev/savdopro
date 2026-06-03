package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/** One open/close working session. */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Filter(name = "accountFilter", condition = "shop_id IN (:shopIds)")
@Entity
@Table(name = "shifts")
@Getter
@Setter
public class Shift extends TenantScopedEntity {

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "opened_by", length = 120)
    private String openedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShiftStatus status = ShiftStatus.OPEN;

    /** Books' expected cash at close (morning balance − cash paid out); null
     *  until the shift is closed. Auto-added by ddl-auto=update on prod. */
    @Column(name = "expected_cash", precision = 15, scale = 2)
    private BigDecimal expectedCash;

    /** Cash physically counted in the till at close; null when not counted. */
    @Column(name = "counted_cash", precision = 15, scale = 2)
    private BigDecimal countedCash;
}
