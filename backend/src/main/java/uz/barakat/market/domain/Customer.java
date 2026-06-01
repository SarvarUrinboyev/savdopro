package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/** A shop customer ("Mijoz"): name plus contact details. */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Filter(name = "accountFilter", condition = "shop_id IN (:shopIds)")
@Entity
@Table(name = "customers")
@Getter
@Setter
public class Customer extends TenantScopedEntity {

    @Column(nullable = false)
    private String name;

    /** Phone number; free-form text, optional. */
    @Column(length = 60)
    private String phone;

    @Column(length = 500)
    private String address;

    @Column(length = 500)
    private String note;

    /** Telegram chat id once the customer links the self-service bot; null until then. */
    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    /**
     * Loyalty points the customer currently has (Phase 4.4).
     * Denormalised cache of {@code sum(customer_transactions.points_delta)};
     * the LoyaltyService recomputes it on every earn / redeem so a
     * malformed ledger entry can't permanently corrupt the visible balance.
     */
    @Column(name = "points_balance", nullable = false)
    private long pointsBalance = 0L;

    /**
     * Lifetime points earned (never decreases). Drives the "Gold customer"
     * tier badges that we'll roll out in a follow-up iteration.
     */
    @Column(name = "points_total_earned", nullable = false)
    private long pointsTotalEarned = 0L;

    /**
     * QR-able loyalty card identifier. Printed on a plastic card so the
     * cashier can scan it at the POS instead of typing a phone. Null
     * until {@link uz.barakat.market.service.LoyaltyService#ensureCardCode}
     * issues one. Unique across all customers.
     */
    @Column(name = "card_code", length = 36, unique = true)
    private String cardCode;

    /** Optional birthday — drives the greeting and birthday-month discount. */
    @Column(name = "birthday")
    private LocalDate birthday;
}
