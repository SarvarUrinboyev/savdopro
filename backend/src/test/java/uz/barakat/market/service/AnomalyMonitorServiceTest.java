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
import uz.barakat.market.dto.AnomalyResponse;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.Sale;
import uz.barakat.market.domain.SaleItem;
import uz.barakat.market.repository.AnomalyAlertRepository;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SaleRepository;
import uz.barakat.market.repository.ShopRepository;

/**
 * The scheduled scan must persist what the detectors find, be idempotent on a
 * re-scan (deduped by (shop_id, dedupe_key)), and let an owner acknowledge an
 * alert so it leaves the banner but stays in history.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnomalyMonitorServiceTest {

    @Autowired AnomalyMonitorService monitor;
    @Autowired AnomalyAlertRepository alerts;
    @Autowired SaleRepository sales;
    @Autowired ProductRepository products;
    @Autowired ShopRepository shops;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void scanPersistsAlerts_isIdempotent_andAcknowledgeLeavesBannerButKeepsHistory() {
        Long shopId = shops.findAll().stream().findFirst().orElseThrow().getId();
        TenantContext.setShopId(shopId);

        // A clearly below-cost sale: cost 100, sold for 0 → ~100 loss (critical).
        Product p = new Product();
        p.setName("Anomaly test mahsulot");
        p.setPurchasePrice(new BigDecimal("100"));
        p.setSalePrice(new BigDecimal("120"));
        p = products.save(p);

        Sale s = new Sale();
        s.setPaymentMethod("NAQD");
        s.setSubtotalUzs(BigDecimal.ZERO);
        s.setTotalUzs(BigDecimal.ZERO);
        SaleItem it = new SaleItem();
        it.setProductId(p.getId());
        it.setProductName(p.getName());
        it.setQuantity(1);
        it.setUnitPriceUzs(BigDecimal.ZERO);
        it.setLineTotalUzs(BigDecimal.ZERO);
        s.addItem(it);
        sales.save(s);

        LocalDate today = LocalDate.now();

        int created = monitor.scanCurrentShop(today);
        assertThat(created).isGreaterThanOrEqualTo(1);

        List<AnomalyResponse> history = monitor.history(today.minusDays(1), today, 100);
        AnomalyResponse belowCost = history.stream()
                .filter(a -> "below-cost-daily".equals(a.code()))
                .findFirst().orElseThrow();
        assertThat(belowCost.severity()).isEqualTo("critical");

        // Idempotent: a second scan of the same day inserts nothing.
        assertThat(monitor.scanCurrentShop(today)).isZero();
        long rows = alerts.findByOccurredOnBetweenOrderByCreatedAtDesc(
                today.minusDays(1), today, org.springframework.data.domain.PageRequest.of(0, 100)).size();
        assertThat(monitor.history(today.minusDays(1), today, 100)).hasSize((int) rows);

        // Banner shows it while unacknowledged.
        assertThat(monitor.activeBanner()).anyMatch(a -> a.id().equals(belowCost.id()));

        // Acknowledge → leaves the banner, stays in history (flagged acknowledged).
        AnomalyResponse acked = monitor.acknowledge(belowCost.id(), "owner");
        assertThat(acked.acknowledged()).isTrue();
        assertThat(acked.acknowledgedBy()).isEqualTo("owner");
        assertThat(monitor.activeBanner()).noneMatch(a -> a.id().equals(belowCost.id()));
        assertThat(monitor.history(today.minusDays(1), today, 100))
                .anyMatch(a -> a.id().equals(belowCost.id()) && a.acknowledged());

        // Re-acknowledge is a no-op (idempotent).
        assertThat(monitor.acknowledge(belowCost.id(), "someone-else").acknowledgedBy()).isEqualTo("owner");
    }
}
