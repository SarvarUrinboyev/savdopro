package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/** One stock-quantity change for a product (the "Ombor harakatlari"). */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Entity
@Table(name = "stock_movements")
@Getter
@Setter
public class StockMovement extends TenantScopedEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Signed change: positive = Kirim, negative = Chiqim. */
    @Column(nullable = false)
    private int delta;

    /** Stock quantity after this movement was applied. */
    @Column(name = "resulting_quantity", nullable = false)
    private int resultingQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StockReason reason;

    @Column(length = 500)
    private String note;
}
