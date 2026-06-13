package uz.barakat.market.publicapi;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.dto.pub.PublicDtos.PaymentResource;
import uz.barakat.market.service.PublicApiQueryService;

/**
 * Open API — accounting export: the payment journal for a period. Tenant-scoped.
 * Scope: {@code accounting:read}.
 */
@RestController
@RequestMapping("/api/v1/accounting")
public class PublicAccountingController {

    private final PublicApiQueryService query;

    public PublicAccountingController(PublicApiQueryService query) {
        this.query = query;
    }

    @GetMapping("/payments")
    public List<PaymentResource> payments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = LocalDate.now();
        return query.payments(from != null ? from : today.minusDays(30), to != null ? to : today);
    }
}
