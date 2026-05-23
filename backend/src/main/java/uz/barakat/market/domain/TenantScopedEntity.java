package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;
import uz.barakat.market.auth.TenantContext;

/**
 * Superclass for every transactional entity that lives inside a shop.
 *
 * <ul>
 *   <li>Adds a NOT NULL {@code shop_id} column.</li>
 *   <li>{@link PrePersist} callback auto-tags inserts with the
 *       request-scoped shop id (populated by {@code TenantFilter}
 *       from the {@code X-Shop-Id} header). Services don't need to
 *       remember to set the column.</li>
 *   <li>The {@code @FilterDef} lives in {@code package-info.java};
 *       each concrete entity carries its own {@code @Filter}
 *       declaration because Hibernate does not inherit it from a
 *       {@code @MappedSuperclass}.</li>
 * </ul>
 */
@MappedSuperclass
@Getter
@Setter
public abstract class TenantScopedEntity extends BaseEntity {

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @PrePersist
    void onTenantPersist() {
        if (shopId == null) {
            Long current = TenantContext.currentShopId();
            if (current != null) {
                shopId = current;
            }
        }
    }
}
