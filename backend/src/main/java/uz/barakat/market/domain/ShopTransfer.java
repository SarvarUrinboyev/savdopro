package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * One cross-shop stock movement. The row is the audit trail — the real
 * stock change is applied by {@code TransferService.create} in a single
 * transaction with two product UPDATEs and two {@link StockMovement}
 * rows (OUT from source, IN to destination).
 *
 * <p>{@code accountId} keeps the row visible to the tenant rollup view
 * without being scoped to a single shop (it bridges two). Both
 * {@code sourceProductId} and {@code destProductId} are denormalised
 * pointers; either can become orphaned if the operator later deletes
 * the product — that's fine, the transfer row is historical.
 */
@Entity
@Table(name = "shop_transfers")
@Getter
@Setter
public class ShopTransfer extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "from_shop_id", nullable = false)
    private Long fromShopId;

    @Column(name = "to_shop_id", nullable = false)
    private Long toShopId;

    @Column(name = "source_product_id")
    private Long sourceProductId;

    @Column(name = "dest_product_id")
    private Long destProductId;

    @Column(name = "product_name", nullable = false, length = 180)
    private String productName;

    @Column(name = "product_barcode", length = 80)
    private String productBarcode;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal qty;

    @Column(length = 500)
    private String note;

    @Column(name = "created_by", length = 120)
    private String createdBy;
}
