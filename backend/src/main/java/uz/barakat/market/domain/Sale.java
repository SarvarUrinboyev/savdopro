package uz.barakat.market.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * One POS sale — the bundle that ties cart items, total, payment and
 * customer together. Receipts, refunds and the "savdolar tarixi" view
 * all start from this aggregate.
 *
 * Booking a sale also creates: a {@link Payment} (the money), N
 * {@link StockMovement}s (the stock change) and optionally a
 * {@link CustomerDebt} entry when paid on credit. Those rows stay the
 * source of truth for their own subsystem; Sale just keeps them tied
 * together via foreign keys so we can rebuild the receipt later.
 */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Filter(name = "accountFilter", condition = "shop_id IN (:shopIds)")
@Entity
@Table(name = "sales")
@Getter
@Setter
public class Sale extends TenantScopedEntity {

    /** Linked {@link Payment} that booked the money — null when fully on credit. */
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "subtotal_uzs", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotalUzs = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "total_uzs", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalUzs = BigDecimal.ZERO;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @Column(length = 500)
    private String note;

    /** Client-generated idempotency key for offline checkout replay (V27). */
    @Column(name = "client_ref", length = 64)
    private String clientRef;

    @Column(name = "refunded_total_uzs", nullable = false, precision = 15, scale = 2)
    private BigDecimal refundedTotalUzs = BigDecimal.ZERO;

    @Column(name = "fully_refunded", nullable = false)
    private boolean fullyRefunded = false;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<SaleItem> items = new ArrayList<>();

    public void addItem(SaleItem item) {
        item.setSale(this);
        items.add(item);
    }
}
