package uz.barakat.market.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.dto.PurchaseDtos.PoRequest;
import uz.barakat.market.dto.PurchaseDtos.PoResponse;
import uz.barakat.market.dto.PurchaseDtos.ReceiveRequest;
import uz.barakat.market.service.PurchaseOrderService;

/** Supplier purchase orders + goods receipt. */
@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    public PurchaseOrderController(PurchaseOrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<PoResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public PoResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PoResponse create(@Valid @RequestBody PoRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public PoResponse update(@PathVariable Long id, @Valid @RequestBody PoRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/order")
    public PoResponse markOrdered(@PathVariable Long id) {
        return service.markOrdered(id);
    }

    @PostMapping("/{id}/receive")
    public PoResponse receive(@PathVariable Long id, @RequestBody ReceiveRequest request) {
        return service.receive(id, request);
    }

    @PostMapping("/{id}/cancel")
    public PoResponse cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
