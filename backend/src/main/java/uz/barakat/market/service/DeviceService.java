package uz.barakat.market.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.SoldDevice;
import uz.barakat.market.domain.StockMovement;
import uz.barakat.market.domain.StockReason;
import uz.barakat.market.dto.DeviceDtos.DeviceResponse;
import uz.barakat.market.dto.DeviceDtos.DeviceStatusRequest;
import uz.barakat.market.dto.DeviceDtos.IntakeRequest;
import uz.barakat.market.dto.PosDtos.DeviceInput;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SoldDeviceRepository;
import uz.barakat.market.repository.StockMovementRepository;

/**
 * Device (IMEI) register: every IMEI-tracked unit becomes a row at INTAKE
 * (status IN_STOCK), flips to SOLD when it's sold to a customer, and can be
 * searched later ("did we sell this IMEI, when, to whom?"). Also lets the owner
 * mark a device BLOCKED/RETURNED and export the IMEIs (e.g. for Knox Guard).
 * All reads are shop-scoped by the tenant filter.
 */
@Service
@Transactional
public class DeviceService {

    private static final List<String> STATUSES =
            List.of("IN_STOCK", "SOLD", "BLOCKED", "RETURNED");

    private final SoldDeviceRepository devices;
    private final ProductRepository products;
    private final StockMovementRepository movements;

    public DeviceService(SoldDeviceRepository devices, ProductRepository products,
                         StockMovementRepository movements) {
        this.devices = devices;
        this.products = products;
        this.movements = movements;
    }

    /**
     * Register incoming units' IMEIs at intake (status IN_STOCK) and bump the
     * product's stock by the number actually registered (one delivery movement).
     * For IMEI-tracked products this is the stock-in path — don't also do a plain
     * quantity kirim for the same units, or stock is double-counted.
     */
    public List<DeviceResponse> intake(IntakeRequest req) {
        Product p = products.findById(req.productId())
                .orElseThrow(() -> NotFoundException.of("Mahsulot", req.productId()));
        LocalDateTime now = LocalDateTime.now();
        List<SoldDevice> created = new ArrayList<>();
        for (DeviceInput in : req.devices()) {
            String imei1 = trim(in.imei1());
            String imei2 = trim(in.imei2());
            String serial = trim(in.serial());
            String appleId = trim(in.appleId());
            if (imei1 == null && imei2 == null && serial == null && appleId == null) {
                continue;   // empty row — skip
            }
            SoldDevice d = new SoldDevice();
            d.setProductId(p.getId());
            d.setProductName(p.getName());
            d.setImei1(imei1);
            d.setImei2(imei2);
            d.setSerialNumber(serial);
            d.setAppleId(appleId);
            d.setStatus("IN_STOCK");
            d.setIntakeDate(now);
            created.add(devices.save(d));
        }
        if (created.isEmpty()) {
            throw new BadRequestException("Hech qanday IMEI kiritilmadi");
        }
        int count = created.size();
        int newQty = p.getQuantity() + count;
        p.setQuantity(newQty);
        products.save(p);

        StockMovement mv = new StockMovement();
        mv.setProductId(p.getId());
        mv.setDelta(count);
        mv.setResultingQuantity(newQty);
        mv.setReason(StockReason.DELIVERY);
        mv.setNote("IMEI kirim (" + count + " dona)");
        mv.setUnitSalePrice(p.getSalePrice());
        mv.setUnitCostPrice(p.getPurchasePrice());
        movements.save(mv);

        return created.stream().map(DeviceService::toResponse).toList();
    }

    /**
     * Chiqim: scan one IMEI out of stock. Finds the IN_STOCK unit by its primary
     * IMEI, flips it to SOLD, and decrements the product's stock by one. Errors
     * (with a clear message) if the IMEI isn't in stock — so a code that was never
     * received, or already gone, can't decrement twice.
     */
    public DeviceResponse dispatchByImei(String imei) {
        String code = trim(imei);
        if (code == null) {
            throw new BadRequestException("IMEI bo'sh");
        }
        SoldDevice d = devices.findFirstByImei1AndStatus(code, "IN_STOCK")
                .orElseThrow(() -> new BadRequestException(
                        "Bu IMEI omborda topilmadi (kirim qilinmagan yoki allaqachon chiqqan): " + code));
        d.setStatus("SOLD");
        d.setSoldAt(LocalDateTime.now());
        if (d.getNote() == null) {
            d.setNote("IMEI chiqim (skaner)");
        }
        SoldDevice saved = devices.save(d);
        if (d.getProductId() != null) {
            products.findById(d.getProductId()).ifPresent(p -> {
                int newQty = Math.max(0, p.getQuantity() - 1);
                p.setQuantity(newQty);
                products.save(p);
                StockMovement mv = new StockMovement();
                mv.setProductId(p.getId());
                mv.setDelta(-1);
                mv.setResultingQuantity(newQty);
                mv.setReason(StockReason.SALE);
                mv.setNote("IMEI chiqim (skaner)");
                mv.setUnitSalePrice(p.getSalePrice());
                mv.setUnitCostPrice(p.getPurchasePrice());
                movements.save(mv);
            });
        }
        return toResponse(saved);
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

    /** Update a device's status (IN_STOCK / SOLD / BLOCKED / RETURNED) and optional note. */
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
        StringBuilder sb = new StringBuilder(
                "IMEI1,IMEI2,Serial,Apple ID,Mahsulot,Mijoz,To'lov,Holat,Kirim,Sotilgan\n");
        for (DeviceResponse d : list(q, status, onlyDebt)) {
            sb.append(csv(d.imei1())).append(',')
              .append(csv(d.imei2())).append(',')
              .append(csv(d.serialNumber())).append(',')
              .append(csv(d.appleId())).append(',')
              .append(csv(d.productName())).append(',')
              .append(csv(d.customerName())).append(',')
              .append(csv(d.paymentMethod())).append(',')
              .append(csv(d.status())).append(',')
              .append(csv(d.intakeDate() == null ? "" : d.intakeDate().toString())).append(',')
              .append(csv(d.soldAt() == null ? "" : d.soldAt().toString())).append('\n');
        }
        return sb.toString();
    }

    private boolean matches(SoldDevice d, String needle) {
        return contains(d.getImei1(), needle)
                || contains(d.getImei2(), needle)
                || contains(d.getSerialNumber(), needle)
                || contains(d.getAppleId(), needle)
                || contains(d.getCustomerName(), needle)
                || contains(d.getProductName(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
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
                d.getImei1(), d.getImei2(), d.getSerialNumber(), d.getAppleId(), d.getCustomerId(),
                d.getCustomerName(), d.getPaymentMethod(), d.getSalePriceUzs(),
                d.getStatus(), d.getNote(), d.getIntakeDate(), d.getSoldAt());
    }
}
