package uz.barakat.market.publicapi;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.dto.pub.PublicDtos.SaleResource;
import uz.barakat.market.service.PublicApiQueryService;

/** Open API — sales (read). Date-windowed, tenant-scoped. Scope: {@code sales:read}. */
@RestController
@RequestMapping("/api/v1/sales")
public class PublicSalesController {

    private final PublicApiQueryService query;

    public PublicSalesController(PublicApiQueryService query) {
        this.query = query;
    }

    @GetMapping
    public List<SaleResource> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = LocalDate.now();
        return query.sales(from != null ? from : today.minusDays(7), to != null ? to : today);
    }

    @GetMapping("/{id}")
    public SaleResource get(@PathVariable Long id) {
        return query.sale(id);
    }
}
