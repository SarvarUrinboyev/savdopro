package uz.barakat.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.StockMovement;
import uz.barakat.market.domain.StockReason;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.ShopRepository;
import uz.barakat.market.repository.StockMovementRepository;

/**
 * Profit reports must value a historical sale at the price snapshot recorded
 * on the movement, NOT the product's current price. Also exercises the
 * {@code salesProfitByProduct} native query against H2 end-to-end.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnalyticsServiceHistoricalPriceTest {

    @Autowired private AnalyticsService analytics;
    @Autowired private ProductRepository products;
    @Autowired private StockMovementRepository movements;
    @Autowired private ShopRepository shops;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void profitUsesPriceSnapshotNotCurrentPrice() {
        Long shopId = shops.findAll().stream().findFirst().orElseThrow().getId();
        TenantContext.setShopId(shopId);

        Product p = new Product();
        p.setName("Snapshot widget");
        p.setPurchasePrice(new BigDecimal("100"));
        p.setSalePrice(new BigDecimal("150"));
        p.setQuantity(10);
        p = products.save(p);

        // A sale of 2 units at the prices in force AT THAT TIME.
        StockMovement m = new StockMovement();
        m.setProductId(p.getId());
        m.setDelta(-2);
        m.setResultingQuantity(8);
        m.setReason(StockReason.SALE);
        m.setUnitSalePrice(new BigDecimal("150"));
        m.setUnitCostPrice(new BigDecimal("100"));
        movements.save(m);

        // Prices change AFTER the sale — must not rewrite the past report.
        p.setSalePrice(new BigDecimal("999"));
        p.setPurchasePrice(new BigDecimal("888"));
        products.save(p);

        List<AnalyticsService.ProductProfitRow> rows =
                analytics.profitByProduct(LocalDate.now().minusDays(1), LocalDate.now());

        Long productId = p.getId();
        AnalyticsService.ProductProfitRow row = rows.stream()
                .filter(r -> r.productId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("product row missing from report"));

        assertThat(row.soldQty()).isEqualTo(2);
        assertThat(row.revenueUsd()).isEqualByComparingTo("300.00"); // 2 x 150 snapshot, not 999
        assertThat(row.costUsd()).isEqualByComparingTo("200.00");    // 2 x 100 snapshot, not 888
        assertThat(row.profitUsd()).isEqualByComparingTo("100.00");
    }
}
