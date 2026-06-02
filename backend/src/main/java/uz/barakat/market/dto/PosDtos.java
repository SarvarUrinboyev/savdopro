package uz.barakat.market.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** REST payloads for the POS checkout + refund endpoints. */
public final class PosDtos {

    private PosDtos() {
    }

    // -------------------------------------------------------------- in

    public record CartItem(
            @NotNull Long productId,
            @Min(1) int quantity,
            /** Per-line discount in UZS. 0 = no line discount. */
            BigDecimal lineDiscountUzs) {
    }

    public record CheckoutRequest(
            @NotEmpty(message = "Savatcha bo'sh bo'lishi mumkin emas") @Valid List<CartItem> items,
            /** Whole-sale percent discount (0–100). */
            BigDecimal discountPercent,
            /** Whole-sale flat-amount discount in UZS. */
            BigDecimal discountAmount,
            /** "NAQD" / "KARTA" / "QARZ" — matches PaymentType enum. */
            @NotBlank String paymentMethod,
            /** Optional customer link (loyalty + receipt name). */
            Long customerId,
            /** Free-text receipt note. */
            String note,
            /** Optional client idempotency key for offline replay (V27). */
            String clientRef) {
    }

    public record RefundItemRequest(
            @NotNull Long saleItemId,
            @Min(1) int quantity) {
    }

    public record RefundRequest(
            /** Empty list = full refund of all remaining items. */
            @Valid List<RefundItemRequest> items,
            String reason) {
    }

    // -------------------------------------------------------------- out

    public record SaleItemResponse(
            Long id,
            Long productId,
            String productName,
            String productSku,
            int quantity,
            int refundedQty,
            BigDecimal unitPriceUzs,
            BigDecimal lineDiscountUzs,
            BigDecimal lineTotalUzs) {
    }

    public record SaleResponse(
            Long id,
            LocalDateTime createdAt,
            Long paymentId,
            Long customerId,
            String customerName,
            BigDecimal subtotalUzs,
            BigDecimal discountAmount,
            BigDecimal discountPercent,
            BigDecimal totalUzs,
            BigDecimal refundedTotalUzs,
            boolean fullyRefunded,
            LocalDateTime refundedAt,
            String paymentMethod,
            String note,
            List<SaleItemResponse> items) {
    }
}
