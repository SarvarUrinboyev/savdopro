package uz.barakat.market.publicapi;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.dto.pub.PublicDtos.OrderResource;
import uz.barakat.market.service.PublicApiQueryService;

/**
 * Open API — supplier incoming-goods orders (read). Tenant-scoped. Optional
 * {@code completed} filter. Scope: {@code orders:read}.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class PublicOrdersController {

    private final PublicApiQueryService query;

    public PublicOrdersController(PublicApiQueryService query) {
        this.query = query;
    }

    @GetMapping
    public List<OrderResource> list(@RequestParam(required = false) Boolean completed,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        return query.orders(completed, page, size);
    }
}
