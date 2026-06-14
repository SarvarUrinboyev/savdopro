package uz.barakat.market.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** REST payloads for the sold-device (IMEI) tracking endpoints. */
public final class DeviceDtos {

    private DeviceDtos() {
    }

    /** API view of one tracked device. */
    public record DeviceResponse(
            Long id,
            Long saleId,
            Long productId,
            String productName,
            String imei1,
            String imei2,
            String serialNumber,
            String appleId,
            Long customerId,
            String customerName,
            String paymentMethod,
            BigDecimal salePriceUzs,
            String status,
            String note,
            LocalDateTime soldAt) {
    }

    /** Update a device's bookkeeping status (ACTIVE / BLOCKED / RETURNED) + note. */
    public record DeviceStatusRequest(
            @NotBlank(message = "Holat kiritilishi shart") String status,
            String note) {
    }
}
