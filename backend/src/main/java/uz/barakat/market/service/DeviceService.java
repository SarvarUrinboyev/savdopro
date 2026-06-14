package uz.barakat.market.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.SoldDevice;
import uz.barakat.market.dto.DeviceDtos.DeviceResponse;
import uz.barakat.market.dto.DeviceDtos.DeviceStatusRequest;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.SoldDeviceRepository;

/**
 * Sold-device (IMEI) tracking: list/search the devices handed to customers,
 * update a device's bookkeeping status, and export IMEIs (e.g. to upload into a
 * Knox Guard device list). All reads are shop-scoped by the tenant filter.
 */
@Service
@Transactional
public class DeviceService {

    private static final List<String> STATUSES = List.of("ACTIVE", "BLOCKED", "RETURNED");

    private final SoldDeviceRepository devices;

    public DeviceService(SoldDeviceRepository devices) {
        this.devices = devices;
    }

    /** All tracked devices, newest first, optionally filtered. */
    @Transactional(readOnly = true)
    public List<DeviceResponse> list(String q, String status, boolean onlyDebt) {
        String needle = q == null ? "" : q.strip().toLowerCase();
        String wantStatus = status == null ? "" : status.strip().toUpperCase();
        return devices.findAllByOrderByCreatedAtDesc().stream()
                .filter(d -> wantStatus.isEmpty() || wantStatus.equals(d.getStatus()))
                .filter(d -> !onlyDebt || "QARZGA".equalsIgnoreCase(d.getPaymentMethod()))
                .filter(d -> needle.isEmpty() || matches(d, needle))
                .map(DeviceService::toResponse)
                .toList();
    }

    /** Update a device's status (ACTIVE / BLOCKED / RETURNED) and optional note. */
    public DeviceResponse updateStatus(Long id, DeviceStatusRequest req) {
        String status = req.status() == null ? "" : req.status().strip().toUpperCase();
        if (!STATUSES.contains(status)) {
            throw new BadRequestException("Noto'g'ri holat: " + req.status());
        }
        SoldDevice d = devices.findById(id)
                .orElseThrow(() -> NotFoundException.of("Qurilma", id));
        d.setStatus(status);
        if (req.note() != null) {
            d.setNote(req.note().isBlank() ? null : req.note().strip());
        }
        return toResponse(devices.save(d));
    }

    /** CSV of the (optionally filtered) devices — IMEI list ready for Knox Guard. */
    @Transactional(readOnly = true)
    public String exportCsv(String q, String status, boolean onlyDebt) {
        StringBuilder sb = new StringBuilder("IMEI1,IMEI2,Serial,Mahsulot,Mijoz,To'lov,Holat,Sana\n");
        for (DeviceResponse d : list(q, status, onlyDebt)) {
            sb.append(csv(d.imei1())).append(',')
              .append(csv(d.imei2())).append(',')
              .append(csv(d.serialNumber())).append(',')
              .append(csv(d.productName())).append(',')
              .append(csv(d.customerName())).append(',')
              .append(csv(d.paymentMethod())).append(',')
              .append(csv(d.status())).append(',')
              .append(csv(d.soldAt() == null ? "" : d.soldAt().toString())).append('\n');
        }
        return sb.toString();
    }

    private boolean matches(SoldDevice d, String needle) {
        return contains(d.getImei1(), needle)
                || contains(d.getImei2(), needle)
                || contains(d.getSerialNumber(), needle)
                || contains(d.getCustomerName(), needle)
                || contains(d.getProductName(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private static String csv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return '"' + escaped + '"';
    }

    private static DeviceResponse toResponse(SoldDevice d) {
        return new DeviceResponse(d.getId(), d.getSaleId(), d.getProductId(), d.getProductName(),
                d.getImei1(), d.getImei2(), d.getSerialNumber(), d.getCustomerId(),
                d.getCustomerName(), d.getPaymentMethod(), d.getSalePriceUzs(),
                d.getStatus(), d.getNote(), d.getSoldAt());
    }
}
