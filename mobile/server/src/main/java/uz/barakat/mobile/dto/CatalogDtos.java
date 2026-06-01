package uz.barakat.mobile.dto;

import uz.barakat.mobile.domain.Banner;
import uz.barakat.mobile.domain.Category;
import uz.barakat.mobile.domain.Product;

import java.util.List;

/** Katalog (kategoriya / mahsulot / banner) javob modellari. */
public final class CatalogDtos {

    private CatalogDtos() {}

    public record CategoryResponse(Long id, String name, String slug, String iconUrl, long productCount) {
        public static CategoryResponse from(Category c, long productCount) {
            return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getIconUrl(), productCount);
        }
    }

    public record ProductResponse(
            Long id, String name, long price, Long oldPrice, String unit,
            String imageUrl, Long categoryId, boolean inStock, Integer discountPercent
    ) {
        public static ProductResponse from(Product p) {
            return new ProductResponse(
                    p.getId(), p.getName(), p.getPrice(), p.getOldPrice(), p.getUnit(),
                    p.getImageUrl(), p.getCategory() != null ? p.getCategory().getId() : null,
                    p.isInStock(), p.getDiscountPercent());
        }
    }

    public record ProductDetailResponse(
            Long id, String name, String description, long price, Long oldPrice, String unit,
            String imageUrl, List<String> images, Long categoryId, String categoryName,
            boolean inStock, Integer discountPercent
    ) {
        public static ProductDetailResponse from(Product p) {
            return new ProductDetailResponse(
                    p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getOldPrice(), p.getUnit(),
                    p.getImageUrl(),
                    p.getImageUrl() != null ? List.of(p.getImageUrl()) : List.of(),
                    p.getCategory() != null ? p.getCategory().getId() : null,
                    p.getCategory() != null ? p.getCategory().getName() : null,
                    p.isInStock(), p.getDiscountPercent());
        }
    }

    public record BannerResponse(Long id, String title, String subtitle, String imageUrl, String actionLink) {
        public static BannerResponse from(Banner b) {
            return new BannerResponse(b.getId(), b.getTitle(), b.getSubtitle(), b.getImageUrl(), b.getActionLink());
        }
    }
}
