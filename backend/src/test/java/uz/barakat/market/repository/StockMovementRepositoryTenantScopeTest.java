package uz.barakat.market.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.Shop;
import uz.barakat.market.domain.StockMovement;
import uz.barakat.market.domain.StockReason;

/**
 * Cross-shop isolation of the native sales/stock report queries.
 *
 * <p>These queries are NOT rewritten by the Hibernate {@code @Filter} (native
 * SQL), so they carry an explicit {@code shop_id IN (:shopIds)} predicate.
 * This test seeds TWO shops with real SALE data and asserts that querying
 * shop A NEVER returns shop B's rows — a genuine exclusion assertion, not a
 * single-shop "the SQL runs" smoke. Runs on the real Flyway-built H2 schema.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StockMovementRepositoryTenantScopeTest {

    @Autowired private StockMovementRepository movements;
    @Autowired private ShopRepository shops;
    @Autowired private ProductRepository products;

    private Long shopA;
    private Long shopB;
    private Long productA;
    private Long productB;

    private static final LocalDateTime FROM = LocalDateTime.now().minusDays(1);
    private static final LocalDateTime TO = LocalDateTime.now().plusDays(1);

    @BeforeEach
    void seed() {
        shopA = newShop("Isolation Shop A");
        shopB = newShop("Isolation Shop B");
        productA = newProduct(shopA, "A-widget", "150", "100");
        productB = newProduct(shopB, "B-widget", "999", "888");
        // One SALE in each shop: 2 units in A, 5 units in B.
        newSale(shopA, productA, 2, "150", "100");
        newSale(shopB, productB, 5, "999", "888");
        TenantContext.clear();
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void salesProfitByProduct_returnsOnlyTheQueriedShop() {
        List<Object[]> rows = movements.salesProfitByProduct(List.of(shopA), FROM, TO);

        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0)[0]).longValue()).isEqualTo(productA);
        assertThat(((Number) rows.get(0)[1]).intValue()).isEqualTo(2);
        // 2 x 150 (shop A) — shop B's 5 x 999 must NOT contribute.
        assertThat(new BigDecimal(rows.get(0)[2].toString())).isEqualByComparingTo("300.00");
        assertThat(rows)
                .as("shop B rows must never leak into shop A's report")
                .noneMatch(r -> ((Number) r[0]).longValue() == productB.longValue());
    }

    @Test
    void sumSalesQtyByProduct_returnsOnlyTheQueriedShop() {
        List<Object[]> rows = movements.sumSalesQtyByProduct(List.of(shopA), FROM, TO);

        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0)[0]).longValue()).isEqualTo(productA);
        assertThat(((Number) rows.get(0)[1]).intValue()).isEqualTo(2);
        assertThat(rows)
                .as("shop B rows must never leak into shop A's quantities")
                .noneMatch(r -> ((Number) r[0]).longValue() == productB.longValue());
    }

    @Test
    void hourlySalesCount_countsOnlyTheQueriedShop() {
        long countA = total(movements.hourlySalesCount(List.of(shopA), FROM, TO));
        long countB = total(movements.hourlySalesCount(List.of(shopB), FROM, TO));
        long both = total(movements.hourlySalesCount(List.of(shopA, shopB), FROM, TO));

        assertThat(countA).as("shop A SALE movements only").isEqualTo(1);
        assertThat(countB).as("shop B SALE movements only").isEqualTo(1);
        assertThat(both).as("consolidated A + B").isEqualTo(2);
    }

    private static long total(List<Object[]> hourly) {
        long sum = 0;
        for (Object[] r : hourly) {
            sum += ((Number) r[1]).longValue();
        }
        return sum;
    }

    private Long newShop(String name) {
        Shop s = new Shop();
        s.setAccountId(1L);   // the Flyway-seeded super-admin account
        s.setName(name);
        return shops.saveAndFlush(s).getId();
    }

    private Long newProduct(Long shopId, String name, String sale, String cost) {
        TenantContext.setShopId(shopId);   // TenantScopedEntity tags shop_id on persist
        try {
            Product p = new Product();
            p.setName(name);
            p.setSalePrice(new BigDecimal(sale));
            p.setPurchasePrice(new BigDecimal(cost));
            p.setQuantity(100);
            return products.saveAndFlush(p).getId();
        } finally {
            TenantContext.clear();
        }
    }

    private void newSale(Long shopId, Long productId, int units, String sale, String cost) {
        TenantContext.setShopId(shopId);
        try {
            StockMovement m = new StockMovement();
            m.setProductId(productId);
            m.setDelta(-units);
            m.setResultingQuantity(100 - units);
            m.setReason(StockReason.SALE);
            m.setUnitSalePrice(new BigDecimal(sale));
            m.setUnitCostPrice(new BigDecimal(cost));
            movements.saveAndFlush(m);
        } finally {
            TenantContext.clear();
        }
    }
}
