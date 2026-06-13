package uz.barakat.market.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.dto.PurchaseDtos.CostHistory;
import uz.barakat.market.dto.PurchaseDtos.ValuationReport;
import uz.barakat.market.service.CostingService;

/** Purchase-price history + FIFO/WAC inventory valuation. */
@RestController
@RequestMapping("/api/costing")
public class CostingController {

    private final CostingService service;

    public CostingController(CostingService service) {
        this.service = service;
    }

    @GetMapping("/history")
    public CostHistory history(@RequestParam Long productId) {
        return service.history(productId);
    }

    @GetMapping("/valuation")
    public ValuationReport valuation() {
        return service.valuation();
    }
}
