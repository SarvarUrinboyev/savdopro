package uz.barakat.market.publicapi;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.dto.pub.PublicDtos.CustomerResource;
import uz.barakat.market.service.PublicApiQueryService;

/** Open API — customers (read, minimal PII). Tenant-scoped. Scope: {@code customers:read}. */
@RestController
@RequestMapping("/api/v1/customers")
public class PublicCustomersController {

    private final PublicApiQueryService query;

    public PublicCustomersController(PublicApiQueryService query) {
        this.query = query;
    }

    @GetMapping
    public List<CustomerResource> list(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        return query.customers(page, size);
    }
}
