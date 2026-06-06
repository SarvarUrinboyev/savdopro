package uz.barakat.market.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create / update payload for a warehouse product. {@code quantity} is the
 * opening stock and is applied only when creating; on update the stock is
 * changed through the dedicated stock-adjustment endpoint.
 */
public record ProductRequest(
        @NotBlank(message = "Mahsulot nomi kiritilishi shart") String name,
        String barcode,
        String imei1,
        String imei2,
        @NotNull(message = "Kelish narxi kiritilishi shart")
        @PositiveOrZero(message = "Narx manfiy bo'la olmaydi") BigDecimal purchasePrice,
        @NotNull(message = "Sotilish narxi kiritilishi shart")
        @PositiveOrZero(message = "Narx manfiy bo'la olmaydi") BigDecimal salePrice,
        @PositiveOrZero(message = "Soni manfiy bo'la olmaydi") Integer quantity,
        Long categoryId,
        /**
         * Optional category name used only when {@code categoryId} is null —
         * e.g. the scanner pre-fills the catalogue's group and the category is
         * created on the fly. Mirrors the bulk importer's resolve-or-create.
         */
        @Size(max = 120, message = "Toifa nomi juda uzun") String categoryName,
        String description,
        @PositiveOrZero(message = "Chegara manfiy bo'la olmaydi") Integer lowStockThreshold,
        String mxikCode,
        @PositiveOrZero(message = "QQS stavkasi manfiy bo'la olmaydi") BigDecimal vatRate,
        String unit,
        LocalDate expiryDate) {
}
