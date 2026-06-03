package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.exception.NotFoundException;

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

    /**
     * Defence in depth against cross-tenant reads. Hibernate's {@code @Filter}
     * scopes HQL / criteria queries, but NOT {@code EntityManager.find()} /
     * Spring-Data {@code findById()} — so a logged-in user could otherwise load
     * another shop's row by guessing its primary key. After every load we check
     * the row belongs to a shop in the caller's active scope.
     *
     * <p>An EMPTY scope means a non-tenant context — the admin / login / shops /
     * health endpoints, or a background job (which runs under {@code GlobalScope},
     * i.e. every shop) — and is trusted. A non-empty scope that does not contain
     * this row's shop means the filter was bypassed: refuse with a 404 so the
     * row's very existence stays hidden.
     */
    @PostLoad
    void enforceTenantScopeOnLoad() {
        List<Long> scope = TenantContext.activeScope();
        if (!scope.isEmpty() && (shopId == null || !scope.contains(shopId))) {
            throw new NotFoundException("Ma'lumot topilmadi");
        }
    }
}
