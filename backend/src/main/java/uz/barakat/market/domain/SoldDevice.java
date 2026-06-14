package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/**
 * One physical device (smartphone / electronics) handed to a customer, tracked
 * by its IMEI(s) and/or serial number.
 *
 * <p>Captured at POS checkout for products flagged {@code requiresImei}, and
 * linked to the sale + customer. The shop uses it as a record (warranty, theft
 * report, debt evidence) and to enrol the IMEI in a device-locking service such
 * as Samsung Knox Guard. {@code status} is the shop's own bookkeeping of any
 * action taken (e.g. marked BLOCKED after a Knox Guard lock) — the app itself
 * never locks a phone.
 */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Filter(name = "accountFilter", condition = "shop_id IN (:shopIds)")
@Entity
@Table(name = "sold_devices")
@Getter
@Setter
public class SoldDevice extends TenantScopedEntity {

    /** The sale this device was part of (null if recorded outside a sale). */
    @Column(name = "sale_id")
    private Long saleId;

    /** The specific receipt line, when known. */
    @Column(name = "sale_item_id")
    private Long saleItemId;

    /** The catalogue product, snapshot name kept below for history. */
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    /** IMEI of the first SIM slot. */
    @Column(length = 40)
    private String imei1;

    /** IMEI of the second SIM slot, for dual-SIM phones; optional. */
    @Column(length = 40)
    private String imei2;

    /** Manufacturer serial number (S/N); optional. */
    @Column(name = "serial_number", length = 80)
    private String serialNumber;

    /** Apple ID (iCloud) signed into an iPhone at sale, for the manual Find My
     *  lock. Login/email only — the password is never stored here. */
    @Column(name = "apple_id", length = 120)
    private String appleId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** How the sale was paid (NAQD / KARTA / QARZGA) — lets the UI flag debt devices. */
    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "sale_price_uzs", precision = 15, scale = 2)
    private BigDecimal salePriceUzs;

    /** ACTIVE | BLOCKED | RETURNED — the shop's own bookkeeping. */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(length = 500)
    private String note;

    @Column(name = "sold_at")
    private LocalDateTime soldAt;
}
