package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/** A product category ("toifa"), e.g. Smartfonlar, Aksessuarlar. */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Entity
@Table(name = "categories")
@Getter
@Setter
public class Category extends TenantScopedEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String name;
}
