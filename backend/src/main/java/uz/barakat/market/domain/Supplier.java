package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/**
 * Yetkazib beruvchi - a supplier we receive goods from. Lightweight
 * contact record; financial balance is derived on read from the payment
 * journal where {@code category = SUPPLIER} and {@code party = name}.
 */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Entity
@Table(name = "suppliers")
@Getter
@Setter
public class Supplier extends TenantScopedEntity {

    @Column(nullable = false, length = 180)
    private String name;

    @Column(length = 40)
    private String phone;

    @Column(length = 400)
    private String address;

    @Column(length = 1000)
    private String note;
}
