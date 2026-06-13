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
import uz.barakat.market.domain.PurchaseOrderStatus;
import uz.barakat.market.domain.Supplier;
import uz.barakat.market.dto.PurchaseDtos.LineRequest;
import uz.barakat.market.dto.PurchaseDtos.PoRequest;
import uz.barakat.market.dto.PurchaseDtos.PoResponse;
import uz.barakat.market.dto.PurchaseDtos.ReceiveLine;
import uz.barakat.market.dto.PurchaseDtos.ReceiveRequest;
import uz.barakat.market.dto.PurchaseDtos.ValuationRow;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.ShopRepository;
import uz.barakat.market.repository.SupplierRepository;

/**
 * Partial receipt advances the PO to PARTIAL then RECEIVED, builds cost layers,
 * and updates the product's weighted-average cost correctly.
 */
@SpringBootTest
@ActiveProfiles("test")
class PurchaseOrderServiceTest {

    @Autowired PurchaseOrderService purchaseOrders;
    @Autowired CostingService costing;
    @Autowired ProductRepository products;
    @Autowired SupplierRepository suppliers;
    @Autowired ShopRepository shops;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void partialThenFullReceiptUpdatesWacAndStatus() {
        Long shopId = shops.findAll().stream().findFirst().orElseThrow().getId();
        TenantContext.setShopId(shopId);

        Product p = new Product();
        p.setName("PO widget");
        p.setPurchasePrice(BigDecimal.ZERO);
        p.setSalePrice(new BigDecimal("20"));
        p.setQuantity(0);
        p = products.save(p);

        Supplier s = new Supplier();
        s.setName("PO supplier");
        s = suppliers.save(s);

        // Order 10 units @ $10.
        PoResponse po = purchaseOrders.create(new PoRequest(
                s.getId(), s.getName(), LocalDate.now(), null, "INV-1", null, null,
                List.of(new LineRequest(p.getId(), null, 10, new BigDecimal("10"), null))));
        purchaseOrders.markOrdered(po.id());
        Long lineId = po.lines().get(0).id();

        // Receive 4 @ $10 → PARTIAL, WAC = 10.
        PoResponse afterPartial = purchaseOrders.receive(po.id(), new ReceiveRequest(
                LocalDate.now(), "INV-1", List.of(new ReceiveLine(lineId, 4, new BigDecimal("10")))));
        assertThat(afterPartial.status()).isEqualTo(PurchaseOrderStatus.PARTIAL);
        Product afterP1 = products.findById(p.getId()).orElseThrow();
        assertThat(afterP1.getQuantity()).isEqualTo(4);
        assertThat(afterP1.getPurchasePrice()).isEqualByComparingTo("10.00");

        // Receive remaining 6 @ $16 → RECEIVED, WAC = (4*10 + 6*16)/10 = 13.60.
        PoResponse afterFull = purchaseOrders.receive(po.id(), new ReceiveRequest(
                LocalDate.now(), "INV-1", List.of(new ReceiveLine(lineId, 6, new BigDecimal("16")))));
        assertThat(afterFull.status()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        Product afterP2 = products.findById(p.getId()).orElseThrow();
        assertThat(afterP2.getQuantity()).isEqualTo(10);
        assertThat(afterP2.getPurchasePrice()).isEqualByComparingTo("13.60");

        // Two cost layers recorded (price history).
        assertThat(costing.history(p.getId()).lots()).hasSize(2);

        // Valuation: WAC = 10*13.60 = 136.00; FIFO = 6*16 + 4*10 = 136.00 (all stock on hand).
        ValuationRow row = costing.valuation().rows().stream()
                .filter(r -> r.productId().equals(afterP2.getId())).findFirst().orElseThrow();
        assertThat(row.wacValueUsd()).isEqualByComparingTo("136.00");
        assertThat(row.fifoValueUsd()).isEqualByComparingTo("136.00");
    }
}
