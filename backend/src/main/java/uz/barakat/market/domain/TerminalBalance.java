package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/** Daily card-terminal totals for the Humo and UzCard payment systems. */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Entity
@Table(name = "terminal_balances")
@Getter
@Setter
public class TerminalBalance extends TenantScopedEntity {

    @Column(name = "date", nullable = false, unique = true)
    private LocalDate date;

    @Column(name = "humo_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal humoAmount = BigDecimal.ZERO;

    @Column(name = "uzcard_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal uzcardAmount = BigDecimal.ZERO;
}
