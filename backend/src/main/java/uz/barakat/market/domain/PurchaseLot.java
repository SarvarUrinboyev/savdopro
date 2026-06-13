package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/**
 * A goods-receipt cost layer: how many units of a product arrived, at what
 * unit cost, when, and from whom. Append-only — this IS the purchase-price
 * history and the source for FIFO valuation (ordered by {@code receiptDate}).
 */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Filter(name = "accountFilter", condition = "shop_id IN (:shopIds)")
@Entity
@Table(name = "purchase_lot")
@Getter
@Setter
public class PurchaseLot extends TenantScopedEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "po_id")
    private Long poId;

    @Column(name = "po_line_id")
    private Long poLineId;

    @Column(name = "supplier_name", length = 180)
    private String supplierName;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(nullable = false)
    private int qty;

    @Column(name = "unit_cost_usd", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCostUsd = BigDecimal.ZERO;

    @Column(name = "invoice_number", length = 64)
    private String invoiceNumber;
}
