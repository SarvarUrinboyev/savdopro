package uz.barakat.market.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.Shop;
import uz.barakat.market.dto.ProductResponse;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.ShopRepository;
import uz.barakat.market.service.ProductService;

/**
 * End-to-end tenant isolation beyond the filter unit tests: the {@code @PostLoad}
 * guard must block a cross-shop {@code findById} (the path the Hibernate row
 * filter can't rewrite), and a service read must only ever return the active
 * shop's rows.
 */
@SpringBootTest
@ActiveProfiles("test")
class TenantIsolationIntegrationTest {

    @Autowired ProductRepository products;
    @Autowired ProductService productService;
    @Autowired ShopRepository shops;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private Product createProduct(Long shopId, String name) {
        TenantContext.setShopId(shopId);
        Product p = new Product();
        p.setName(name);
        p.setPurchasePrice(BigDecimal.ONE);
        p.setSalePrice(new BigDecimal("2"));
        p.setQuantity(1);
        Product saved = products.save(p);
        TenantContext.clear();
        return saved;
    }

    @Test
    void postLoadGuardBlocksCrossShopFindById() {
        Long shopA = shops.findAll().stream().findFirst().orElseThrow().getId();
        Product p = createProduct(shopA, "Isolation widget A");

        // Switch the active scope to a DIFFERENT shop, then try to load A's row
        // by its primary key — the @PostLoad guard must hide it (404), not leak it.
        TenantContext.setShopId(shopA + 9999);
        assertThatThrownBy(() -> products.findById(p.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void serviceReadReturnsOnlyActiveShopRows() {
        Long shopA = shops.findAll().stream().findFirst().orElseThrow().getId();
        Long accountId = shops.findById(shopA).orElseThrow().getAccountId();

        Shop b = new Shop();
        b.setAccountId(accountId);
        b.setName("Isolation shop B");
        b.setMain(false);
        Long shopB = shops.save(b).getId();

        createProduct(shopA, "A-only widget");
        createProduct(shopB, "B-only widget");

        TenantContext.setShopId(shopA);
        List<ProductResponse> aList = productService.list(null, null, null);
        List<String> names = aList.stream().map(ProductResponse::name).toList();
        assertThat(names).contains("A-only widget");
        assertThat(names).doesNotContain("B-only widget");
    }
}
