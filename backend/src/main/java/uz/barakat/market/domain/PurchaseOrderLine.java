package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/** One product line of a {@link PurchaseOrder}. */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Filter(name = "accountFilter", condition = "shop_id IN (:shopIds)")
@Entity
@Table(name = "purchase_order_line")
@Getter
@Setter
public class PurchaseOrderLine extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "ordered_qty", nullable = false)
    private int orderedQty;

    @Column(name = "received_qty", nullable = false)
    private int receivedQty;

    @Column(name = "unit_cost_usd", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCostUsd = BigDecimal.ZERO;

    @Column(length = 300)
    private String note;
}
