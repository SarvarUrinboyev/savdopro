package uz.barakat.market.controller;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.dto.DeviceDtos.DeviceResponse;
import uz.barakat.market.dto.DeviceDtos.DeviceStatusRequest;
import uz.barakat.market.dto.DeviceDtos.DispatchRequest;
import uz.barakat.market.dto.DeviceDtos.IntakeRequest;
import uz.barakat.market.service.DeviceService;

/** REST API for sold-device (IMEI) tracking. */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    /** Register incoming units' IMEIs at intake (IN_STOCK) + bump stock. */
    @PostMapping("/intake")
    public List<DeviceResponse> intake(@Valid @RequestBody IntakeRequest request) {
        return service.intake(request);
    }

    /** Chiqim: scan one IMEI out of stock — marks it SOLD + decrements stock. */
    @PostMapping("/dispatch")
    public DeviceResponse dispatch(@Valid @RequestBody DispatchRequest request) {
        return service.dispatchByImei(request.imei());
    }

    /** List/search tracked devices; q matches IMEI/serial/customer/product. */
    @GetMapping
    public List<DeviceResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "false") boolean onlyDebt) {
        return service.list(q, status, onlyDebt);
    }

    /** Update a device's bookkeeping status (ACTIVE / BLOCKED / RETURNED). */
    @PatchMapping("/{id}")
    public DeviceResponse updateStatus(@PathVariable Long id,
                                       @Valid @RequestBody DeviceStatusRequest request) {
        return service.updateStatus(id, request);
    }

    /** CSV export of the (optionally filtered) device list — IMEIs for Knox Guard upload. */
    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "false") boolean onlyDebt) {
        byte[] body = service.exportCsv(q, status, onlyDebt).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"devices.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }
}
