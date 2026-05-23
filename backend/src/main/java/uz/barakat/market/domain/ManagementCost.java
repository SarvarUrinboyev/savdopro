package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/**
 * A management cost entry (salary, tax or other). Subtracted from gross
 * profit on the Management page to give the net profit.
 */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Entity
@Table(name = "management_costs")
@Getter
@Setter
public class ManagementCost extends TenantScopedEntity {

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ManagementCostType type;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** Currency this cost was entered in (UZS by default). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency = Currency.UZS;

    @Column(length = 500)
    private String note;
}
