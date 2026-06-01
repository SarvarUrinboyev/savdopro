package uz.barakat.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * API view of a customer with ledger totals. {@code balance} is
 * {@code goodsTotal - paidTotal}: a positive balance means the customer
 * owes the shop, a negative balance is credit the shop holds for them.
 */
public record CustomerResponse(
        Long id,
        String name,
        String phone,
        String address,
        String note,
        BigDecimal goodsTotal,
        BigDecimal paidTotal,
        BigDecimal balance,
        int transactionCount,
        LocalDateTime createdAt,
        long pointsBalance,
        long pointsTotalEarned,
        LocalDate birthday,
        String tier,
        int tierDiscountPercent,
        boolean birthdayThisMonth,
        /** True when the customer has linked the self-service Telegram bot. */
        boolean telegramLinked) {
}
