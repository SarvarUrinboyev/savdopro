package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/** "Debt owed to us": an amount a customer owes the owner. */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Entity
@Table(name = "customer_debts")
@Getter
@Setter
public class CustomerDebt extends TenantScopedEntity {

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "original_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "paid_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean paid = false;

    @Column(length = 500)
    private String note;
}
