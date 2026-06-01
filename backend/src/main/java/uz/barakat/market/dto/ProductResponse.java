package uz.barakat.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * API view of a warehouse product. {@code margin} is the per-unit profit
 * (sale - purchase); {@code stockValue} is purchasePrice x quantity;
 * {@code stockStatus} is {@code IN_STOCK} / {@code LOW} / {@code OUT}.
 */
public record ProductResponse(
        Long id,
        String name,
        String barcode,
        String imei1,
        String imei2,
        BigDecimal purchasePrice,
        BigDecimal salePrice,
        int quantity,
        Long categoryId,
        String categoryName,
        String description,
        int lowStockThreshold,
        BigDecimal margin,
        BigDecimal stockValue,
        String stockStatus,
        String mxikCode,
        BigDecimal vatRate,
        String unit,
        LocalDate expiryDate,
        LocalDateTime createdAt) {
}
