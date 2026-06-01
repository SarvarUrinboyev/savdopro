package uz.barakat.mobile.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.mobile.dto.CatalogDtos.BannerResponse;
import uz.barakat.mobile.dto.CatalogDtos.CategoryResponse;
import uz.barakat.mobile.dto.CatalogDtos.ProductDetailResponse;
import uz.barakat.mobile.dto.CatalogDtos.ProductResponse;
import uz.barakat.mobile.dto.PageResponse;
import uz.barakat.mobile.service.CatalogService;

import java.util.List;

@RestController
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalog.categories();
    }

    @GetMapping("/products")
    public PageResponse<ProductResponse> products(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return catalog.products(categoryId, q, page, size, sort);
    }

    @GetMapping("/products/popular")
    public List<ProductResponse> popular() {
        return catalog.popular();
    }

    @GetMapping("/products/{id}")
    public ProductDetailResponse product(@PathVariable Long id) {
        return catalog.product(id);
    }

    @GetMapping("/banners")
    public List<BannerResponse> banners() {
        return catalog.banners();
    }
}
