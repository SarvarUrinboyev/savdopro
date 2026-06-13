package uz.barakat.market.dto.pub;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * External-stable DTOs for the Open API ({@code /api/v1/**}) and webhook
 * payloads. Deliberately a curated, public-facing shape — internal fields such
 * as {@code purchasePrice}/cost/margin are NEVER exposed. Grouped in one file
 * per the codebase convention ({@code PosDtos}, {@code AccountingDtos}).
 */
public final class PublicDtos {

    private PublicDtos() {
    }

    /** A catalogue item: price + current stock, no cost/margin. */
    public record ProductResource(
            Long id, String name, String barcode, String sku,
            BigDecimal price, int stockQty, boolean available,
            Long categoryId, String unit, BigDecimal vatRate,
            int lowStockThreshold, LocalDate expiryDate, LocalDateTime createdAt) {
    }

    public record SaleLineResource(
            Long productId, String productName, String sku, int quantity,
            BigDecimal unitPrice, BigDecimal lineDiscount, BigDecimal lineTotal, int refundedQty) {
    }

    public record SaleResource(
            Long id, LocalDateTime createdAt, BigDecimal subtotal, BigDecimal discount,
            BigDecimal total, String paymentMethod, BigDecimal refundedTotal,
            boolean fullyRefunded, LocalDateTime refundedAt, String cashier,
            List<SaleLineResource> items) {
    }

    public record CustomerResource(
            Long id, String name, String phone, long pointsBalance, LocalDateTime createdAt) {
    }

    /** A supplier incoming-goods order ("buyurtma"). */
    public record OrderResource(
            Long id, String name, String supplier, LocalDate orderDate, LocalDate deliveryDate,
            BigDecimal amount, boolean completed, LocalDateTime completedAt, String note) {
    }

    public record PaymentResource(
            Long id, LocalDate date, String direction, String category, String party,
            BigDecimal amount, String method, String currency, String note,
            Long customerId, LocalDateTime createdAt) {
    }

    /** The signed envelope every webhook delivery carries. */
    public record WebhookEnvelope(String event, LocalDateTime occurredAt, Long shopId, Object data) {
    }
}
