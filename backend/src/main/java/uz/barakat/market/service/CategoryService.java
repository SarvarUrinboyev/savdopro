package uz.barakat.market.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.domain.Category;
import uz.barakat.market.domain.Product;
import uz.barakat.market.dto.CategoryRequest;
import uz.barakat.market.dto.CategoryResponse;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.CategoryRepository;
import uz.barakat.market.repository.ProductRepository;

/** Product categories ("toifalar"). */
@Service
@Transactional
public class CategoryService {

    /**
     * Starter categories seeded the first time a brand-new shop opens its
     * category list, so the product editor's "Toifa" dropdown is never empty
     * for a fresh account. The shop can rename/delete these or add its own.
     */
    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "Umumiy", "Oziq-ovqat", "Ichimliklar", "Maishiy tovarlar", "Gigiyena", "Boshqa");

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    // Writable (not readOnly) so a fresh shop can be seeded with defaults on
    // its first category load. After the one-time seed this only reads.
    public List<CategoryResponse> list() {
        seedDefaultsIfEmpty();
        Map<Long, Long> counts = products.findAll().stream()
                .filter(p -> p.getCategoryId() != null)
                .collect(Collectors.groupingBy(Product::getCategoryId, Collectors.counting()));
        return categories.findAllByOrderByNameAsc().stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName(),
                        counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    /**
     * Seeds the default categories for the current shop the first time it has
     * none. Only runs in single-shop mode (a concrete shop is active) — in the
     * consolidated "all shops" view there is no single shop_id to attribute the
     * new rows to, so seeding is skipped (each shop is seeded when opened).
     */
    private void seedDefaultsIfEmpty() {
        if (TenantContext.currentShopId() == null) {
            return;
        }
        if (!categories.findAllByOrderByNameAsc().isEmpty()) {
            return;
        }
        for (String name : DEFAULT_CATEGORIES) {
            Category category = new Category();
            category.setName(name);
            categories.save(category);
        }
    }

    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().strip();
        categories.findFirstByNameIgnoreCase(name).ifPresent(existing -> {
            throw new BadRequestException("Bunday toifa allaqachon mavjud: " + name);
        });
        Category category = new Category();
        category.setName(name);
        categories.save(category);
        return new CategoryResponse(category.getId(), category.getName(), 0L);
    }

    public void delete(Long id) {
        Category category = categories.findById(id)
                .orElseThrow(() -> NotFoundException.of("Toifa", id));
        // Products keep existing; their category_id is cleared by the FK rule.
        categories.delete(category);
    }

    /** Finds a category by name, creating it when missing. Used by the importer. */
    public Long resolveOrCreate(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String clean = name.strip();
        return categories.findFirstByNameIgnoreCase(clean)
                .map(Category::getId)
                .orElseGet(() -> {
                    Category category = new Category();
                    category.setName(clean);
                    return categories.save(category).getId();
                });
    }
}
