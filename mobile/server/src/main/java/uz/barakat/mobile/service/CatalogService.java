package uz.barakat.mobile.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.barakat.mobile.domain.Product;
import uz.barakat.mobile.dto.CatalogDtos.BannerResponse;
import uz.barakat.mobile.dto.CatalogDtos.CategoryResponse;
import uz.barakat.mobile.dto.CatalogDtos.ProductDetailResponse;
import uz.barakat.mobile.dto.CatalogDtos.ProductResponse;
import uz.barakat.mobile.dto.PageResponse;
import uz.barakat.mobile.repository.BannerRepository;
import uz.barakat.mobile.repository.CategoryRepository;
import uz.barakat.mobile.repository.ProductRepository;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final BannerRepository banners;

    public CatalogService(CategoryRepository categories, ProductRepository products, BannerRepository banners) {
        this.categories = categories;
        this.products = products;
        this.banners = banners;
    }

    public List<CategoryResponse> categories() {
        return categories.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(c -> CategoryResponse.from(c, products.countByCategoryIdAndActiveTrue(c.getId())))
                .toList();
    }

    public PageResponse<ProductResponse> products(Long categoryId, String q, int page, int size, String sort) {
        String query = (q != null && q.isBlank()) ? null : q;
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), sortOf(sort));
        Page<Product> result = products.search(categoryId, query, pageable);
        return PageResponse.of(result, ProductResponse::from);
    }

    public ProductDetailResponse product(Long id) {
        Product p = products.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Mahsulot topilmadi"));
        return ProductDetailResponse.from(p);
    }

    public List<ProductResponse> popular() {
        return products.findTop10ByActiveTrueAndPopularTrueOrderByIdDesc().stream()
                .map(ProductResponse::from)
                .toList();
    }

    public List<BannerResponse> banners() {
        return banners.findAllByActiveTrueOrderBySortOrderAsc().stream()
                .map(BannerResponse::from)
                .toList();
    }

    private int clampSize(int size) {
        if (size < 1) return 20;
        return Math.min(size, 100);
    }

    private Sort sortOf(String sort) {
        if (sort == null) return Sort.by(Sort.Direction.DESC, "popular").and(Sort.by("id").descending());
        return switch (sort) {
            case "price_asc" -> Sort.by("price").ascending();
            case "price_desc" -> Sort.by("price").descending();
            case "new" -> Sort.by("id").descending();
            default -> Sort.by(Sort.Direction.DESC, "popular").and(Sort.by("id").descending());
        };
    }
}
