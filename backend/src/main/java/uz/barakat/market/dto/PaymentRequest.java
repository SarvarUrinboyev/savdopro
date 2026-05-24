package uz.barakat.market.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import uz.barakat.market.domain.Currency;
import uz.barakat.market.domain.PaymentCategory;
import uz.barakat.market.domain.PaymentDirection;
import uz.barakat.market.domain.PaymentType;

/**
 * Create/update payload for a payment-journal entry.
 *
 * <p>{@code discountAmount} + {@code discountPercent} are receipt-level
 * metadata only — the {@code amount} field already represents the NET
 * price the customer paid (post-discount). {@code customerId} is the
 * loyalty hook: when present on an INCOMING payment, the service awards
 * points to that customer.
 */
public record PaymentRequest(
        LocalDate date,
        @NotNull(message = "Yo'nalish tanlanishi shart") PaymentDirection direction,
        @NotNull(message = "Turi tanlanishi shart") PaymentCategory category,
        String party,
        @NotNull(message = "Summa kiritilishi shart")
        @Positive(message = "Summa musbat bo'lishi kerak") BigDecimal amount,
        @NotNull(message = "To'lov usuli tanlanishi shart") PaymentType method,
        Currency currency,
        String note,
        @PositiveOrZero(message = "Chegirma manfiy bo'lmasligi kerak")
        BigDecimal discountAmount,
        @PositiveOrZero(message = "Chegirma % manfiy bo'lmasligi kerak")
        BigDecimal discountPercent,
        Long customerId) {
}
