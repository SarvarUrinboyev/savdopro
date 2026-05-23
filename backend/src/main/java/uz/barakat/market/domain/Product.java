package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/** A warehouse / inventory item (phone, accessory, electronics). */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Entity
@Table(name = "products")
@Getter
@Setter
public class Product extends TenantScopedEntity {

    @Column(nullable = false)
    private String name;

    /** Barcode (EAN/UPC) printed on the box; read by a USB scanner. */
    @Column(length = 64)
    private String barcode;

    /** IMEI of the first SIM slot; only meaningful for phones, optional. */
    @Column(length = 40)
    private String imei1;

    /** IMEI of the second SIM slot, for dual-SIM phones; optional. */
    @Column(length = 40)
    private String imei2;

    /** Cost price - what the shop paid for the item ("kelish narxi"). */
    @Column(name = "purchase_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal purchasePrice = BigDecimal.ZERO;

    /** Selling price to the customer ("sotilish narxi"). */
    @Column(name = "sale_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal salePrice = BigDecimal.ZERO;

    /** Units currently in stock ("qoldiq soni"). */
    @Column(nullable = false)
    private int quantity = 0;

    /** Owning category ("toifa"); null when uncategorised. */
    @Column(name = "category_id")
    private Long categoryId;

    @Column(length = 2000)
    private String description;

    /** Low-stock warning level; 0 disables the warning. */
    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 0;

    @Column(length = 500)
    private String note;

    /** IKPU / MXIK national catalogue code; used by e-invoices and fiscal receipts. */
    @Column(name = "mxik_code", length = 30)
    private String mxikCode;

    /** VAT (QQS) rate as a percentage, e.g. 12.00; null when not set. */
    @Column(name = "vat_rate", precision = 5, scale = 2)
    private BigDecimal vatRate;

    /** Unit of measure ("dona", "kg", "litr", ...). Defaults to pieces. */
    @Column(nullable = false, length = 24)
    private String unit = "dona";
}
