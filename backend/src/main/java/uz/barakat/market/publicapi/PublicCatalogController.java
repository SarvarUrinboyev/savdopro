package uz.barakat.market.publicapi;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.dto.pub.PublicDtos.ProductResource;
import uz.barakat.market.service.PublicApiQueryService;

/**
 * Open API — product catalogue + stock. Reads go through
 * {@link PublicApiQueryService} so the tenant filter is enabled (scoped to the
 * API key's shop). Scope: {@code catalog:read}.
 */
@RestController
@RequestMapping("/api/v1/products")
public class PublicCatalogController {

    private final PublicApiQueryService query;

    public PublicCatalogController(PublicApiQueryService query) {
        this.query = query;
    }

    @GetMapping
    public List<ProductResource> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return query.products(page, size);
    }

    @GetMapping("/{id}")
    public ProductResource get(@PathVariable Long id) {
        return query.product(id);
    }
}
