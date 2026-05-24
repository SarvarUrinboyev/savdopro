package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A single physical shop owned by an {@link Account}. One account may
 * have many shops; exactly one of them is flagged {@code isMain} and
 * acts as the consolidated rollup.
 *
 * <h2>Per-shop register profile (Phase 3.3)</h2>
 * <ul>
 *   <li>{@code printerName} — name of the receipt printer as seen by
 *       Windows (e.g. {@code "Xprinter XP-58"}). The desktop's print
 *       dialog defaults to this when a sale is rung up at this shop.
 *       Null means "use the OS default printer".</li>
 *   <li>{@code cashRegisterNo} — local cash-register / tabletop id
 *       printed on receipts and tax filings.</li>
 *   <li>{@code receiptFooter} — free-form footer line on every receipt
 *       (e.g. social media handle, return policy).</li>
 * </ul>
 */
@Entity
@Table(name = "shops")
@Getter
@Setter
public class Shop extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "is_main", nullable = false)
    private boolean main = false;

    @Column(length = 300)
    private String address;

    @Column(name = "contact_phone", length = 40)
    private String contactPhone;

    @Column(name = "printer_name", length = 120)
    private String printerName;

    @Column(name = "cash_register_no", length = 40)
    private String cashRegisterNo;

    @Column(name = "receipt_footer", length = 300)
    private String receiptFooter;
}
