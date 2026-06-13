package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/**
 * One line of the Chart of Accounts ("Hisoblar rejasi"). Shop-scoped; the
 * standard chart is seeded per shop by {@code ChartOfAccountsService}.
 */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Filter(name = "accountFilter", condition = "shop_id IN (:shopIds)")
@Entity
@Table(name = "gl_account")
@Getter
@Setter
public class GlAccount extends TenantScopedEntity {

    /** Stable numeric code, e.g. "1100" (Kassa). Unique within a shop. */
    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private GlAccountType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false, length = 6)
    private NormalBalance normalBalance;

    /** Optional parent account for a roll-up tree. */
    @Column(name = "parent_id")
    private Long parentId;

    /** Seeded accounts that auto-posting targets — protected from deletion. */
    @Column(name = "is_system", nullable = false)
    private boolean system = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 500)
    private String description;
}
