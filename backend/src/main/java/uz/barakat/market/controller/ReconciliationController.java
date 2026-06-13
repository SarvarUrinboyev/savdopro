package uz.barakat.market.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.dto.ReconciliationDtos.CreditResult;
import uz.barakat.market.dto.ReconciliationDtos.ReconciliationResponse;
import uz.barakat.market.service.OnlinePaymentService;
import uz.barakat.market.service.ReconciliationService;

/**
 * Bank/payments reconciliation. Under {@code /api/accounting/**}, so it reuses
 * the MANAGEMENT permission (owner / finance only).
 */
@RestController
@RequestMapping("/api/accounting/reconciliation")
public class ReconciliationController {

    private final ReconciliationService service;
    private final OnlinePaymentService onlinePayments;

    public ReconciliationController(ReconciliationService service,
                                    OnlinePaymentService onlinePayments) {
        this.service = service;
        this.onlinePayments = onlinePayments;
    }

    @GetMapping
    public ReconciliationResponse reconcile(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today;
        return service.reconcile(start, end);
    }

    /** Repairs a paid-but-not-credited online payment by writing the ledger row. */
    @PostMapping("/online/{id}/credit")
    public CreditResult creditOnline(@PathVariable Long id) {
        boolean credited = onlinePayments.creditUnreconciled(id);
        return new CreditResult(credited,
                credited ? "Qarzga yozildi" : "Bu to'lov allaqachon yozilgan yoki holati mos emas");
    }
}
