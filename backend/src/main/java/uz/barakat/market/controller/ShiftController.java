package uz.barakat.market.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.dto.EndOfDayReport;
import uz.barakat.market.dto.ShiftCloseRequest;
import uz.barakat.market.dto.ShiftOpenRequest;
import uz.barakat.market.dto.ShiftResponse;
import uz.barakat.market.service.ShiftService;

/** REST API for opening/closing shifts and the shift history. */
@RestController
@RequestMapping("/api/shifts")
public class ShiftController {

    private final ShiftService service;

    public ShiftController(ShiftService service) {
        this.service = service;
    }

    @GetMapping
    public List<ShiftResponse> history() {
        return service.history();
    }

    /** The open shift, or HTTP 204 when none is open. */
    @GetMapping("/current")
    public ResponseEntity<ShiftResponse> current() {
        ShiftResponse current = service.current();
        return current == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(current);
    }

    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftResponse open(@Valid @RequestBody ShiftOpenRequest request) {
        return service.open(request);
    }

    /** Closes the shift, reconciles the counted cash, builds the report and
     *  sends it to Telegram. The cash count body is optional. */
    @PostMapping("/close")
    public EndOfDayReport close(@RequestBody(required = false) @Valid ShiftCloseRequest request) {
        return service.close(request);
    }

    @DeleteMapping("/history")
    public Map<String, Object> clearHistory() {
        return Map.of("removed", service.clearHistory());
    }
}
