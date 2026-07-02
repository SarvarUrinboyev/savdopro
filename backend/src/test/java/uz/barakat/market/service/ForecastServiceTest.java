package uz.barakat.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.domain.Product;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.StockMovementRepository;

/**
 * The recency-weighted forecast: a product selling faster in the last 14 days
 * than before gets a higher blended velocity, a rising trend factor, and a
 * cushioned re-order suggestion; a steady product keeps trend ≈ 1.
 */
@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    @Mock private ProductRepository products;
    @Mock private StockMovementRepository movements;

    private ForecastService forecast;

    @BeforeEach
    void setUp() {
        TenantContext.setShopId(1L);
        forecast = new ForecastService(products, movements);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static Product product(long id, String name, int qty) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setQuantity(qty);
        return p;
    }

    /** forecast() queries the full window first, then the recent window. */
    private void stubSales(double full30, double recent14) {
        when(movements.sumSalesQtyByProduct(anyList(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{1L, full30}))
                .thenReturn(List.<Object[]>of(new Object[]{1L, recent14}));
    }

    @Test
    void risingProductGetsHigherVelocityTrendAndCushionedReorder() {
        // 30 units in 30 days, 28 of them in the last 14 -> clearly taking off.
        when(products.findAll()).thenReturn(List.of(product(1L, "Cola", 5)));
        stubSales(30.0, 28.0);

        var f = forecast.forecast().get(0);

        // Blend 0.6*(28/14) + 0.4*(30/30) = 1.2 + 0.4 = 1.6/day (flat would say 1.0)
        assertThat(f.dailyVelocity()).isEqualTo(1.6);
        // Trend = 2.0/day recent vs (30-28)/16 = 0.125/day before -> 16x, rising
        assertThat(f.trendFactor()).isGreaterThan(1.5);
        assertThat(f.reorderNeeded()).isTrue(); // 5 / 1.6 = 3 days of stock
        // Cushioned: ceil(1.6 * 21 * 1.25) = 42, not the flat ceil(1.6*21)=34
        assertThat(f.suggestedReorderQty()).isEqualTo(42);
    }

    @Test
    void steadyProductKeepsTrendNearOneAndUncushionedSuggestion() {
        // 30 in 30 days, 14 of them in the last 14 -> perfectly steady 1/day.
        when(products.findAll()).thenReturn(List.of(product(1L, "Non", 3)));
        stubSales(30.0, 14.0);

        var f = forecast.forecast().get(0);

        assertThat(f.dailyVelocity()).isEqualTo(1.0);
        assertThat(f.trendFactor()).isEqualTo(1.0);
        assertThat(f.suggestedReorderQty()).isEqualTo(21); // ceil(1.0 * 21)
    }

    @Test
    void neverSoldProductHasNoRunOutAndNoReorder() {
        when(products.findAll()).thenReturn(List.of(product(1L, "Chang'i", 9)));
        when(movements.sumSalesQtyByProduct(anyList(), any(), any()))
                .thenReturn(List.of())
                .thenReturn(List.of());

        var f = forecast.forecast().get(0);

        assertThat(f.dailyVelocity()).isZero();
        assertThat(f.daysOfStock()).isNull();
        assertThat(f.reorderNeeded()).isFalse();
        assertThat(f.suggestedReorderQty()).isZero();
    }
}
