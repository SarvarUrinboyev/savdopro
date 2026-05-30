package uz.barakat.market.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.dto.PageSlice;
import uz.barakat.market.dto.PosDtos.CheckoutRequest;
import uz.barakat.market.dto.PosDtos.RefundRequest;
import uz.barakat.market.dto.PosDtos.SaleResponse;
import uz.barakat.market.service.PosService;

/**
 * REST API for the POS module:
 *
 * <ul>
 *   <li>{@code POST /api/pos/checkout}        — book a new sale</li>
 *   <li>{@code GET  /api/pos/sales?page=&size=} — paginated sales (newest first)</li>
 *   <li>{@code GET  /api/pos/sales/{id}}      — single sale + items</li>
 *   <li>{@code POST /api/pos/sales/{id}/refund} — full or partial refund</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/pos")
public class PosController {

    private final PosService service;

    public PosController(PosService service) {
        this.service = service;
    }

    @PostMapping("/checkout")
    public SaleResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return service.checkout(request);
    }

    @GetMapping("/sales")
    public PageSlice<SaleResponse> recent(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.recent(page, size);
    }

    @GetMapping("/sales/{id}")
    public SaleResponse get(@PathVariable Long id) {
        return service.byId(id);
    }

    @PostMapping("/sales/{id}/refund")
    public SaleResponse refund(@PathVariable Long id,
                               @Valid @RequestBody(required = false) RefundRequest request) {
        return service.refund(id, request);
    }
}
