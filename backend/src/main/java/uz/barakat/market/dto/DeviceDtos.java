package uz.barakat.market.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import uz.barakat.market.dto.PosDtos.DeviceInput;

/** REST payloads for the device (IMEI) tracking + intake endpoints. */
public final class DeviceDtos {

    private DeviceDtos() {
    }

    /** Register incoming units' IMEIs at intake; bumps the product's stock by the count. */
    public record IntakeRequest(
            @NotNull(message = "Mahsulot tanlanmagan") Long productId,
            @NotEmpty(message = "Kamida bitta IMEI kiriting") List<DeviceInput> devices) {
    }

    /** Scan one IMEI out of stock (chiqim): finds the IN_STOCK unit and marks it gone. */
    public record DispatchRequest(
            @NotBlank(message = "IMEI kiritilmadi") String imei) {
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
            LocalDateTime intakeDate,
            LocalDateTime soldAt) {
    }

    /** Update a device's bookkeeping status (ACTIVE / BLOCKED / RETURNED) + note. */
    public record DeviceStatusRequest(
            @NotBlank(message = "Holat kiritilishi shart") String status,
            String note) {
    }
}
