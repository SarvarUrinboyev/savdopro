package uz.barakat.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.Shop;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.ShopRepository;
import uz.barakat.market.repository.ShopTransferRepository;
import uz.barakat.market.service.TransferService.TransferRequest;
import uz.barakat.market.service.TransferService.TransferResponse;

/**
 * Regression tests for the cross-shop transfer bugs the audit flagged:
 * <ul>
 *   <li>{@code quantity} (INTEGER column) was cast to {@code BigDecimal} →
 *       {@code ClassCastException} on every transfer.</li>
 *   <li>The clone path selected/inserted {@code ikpu_code}, a column that does
 *       not exist (the schema has {@code mxik_code}) → SQL error on
 *       transfer-to-new-product.</li>
 * </ul>
 * Backed by a real H2 + the full Flyway chain so the native SQL runs for real.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransferServiceTest {

    @Autowired TransferService transfers;
    @Autowired ShopRepository shops;
    @Autowired ProductRepository products;
    @Autowired ShopTransferRepository transferRows;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    /** The account that owns the seeded main shop — every test runs inside it. */
    private Long seededAccountId() {
        return shops.findAll().stream().findFirst().orElseThrow().getAccountId();
    }

    private Long mainShopId() {
        return shops.findAll().stream().findFirst().orElseThrow().getId();
    }

    private Long newShop(Long accountId, String name) {
        Shop s = new Shop();
        s.setAccountId(accountId);
        s.setName(name);
        s.setMain(false);
        return shops.save(s).getId();
    }

    private Product newProduct(Long shopId, String name, String barcode,
                              int qty, String mxik) {
        TenantContext.setShopId(shopId);
        Product p = new Product();
        p.setName(name);
        p.setBarcode(barcode);
        p.setPurchasePrice(new BigDecimal("3.00"));
        p.setSalePrice(new BigDecimal("5.00"));
        p.setQuantity(qty);
        p.setMxikCode(mxik);
        Product saved = products.save(p);
        TenantContext.clear();
        return saved;
    }

    private int quantityOf(Long productId) {
        return products.findById(productId).orElseThrow().getQuantity();
    }

    @Test
    void transferClonesProductToNewShopMovesStockAndCopiesMxik() {
        Long account = seededAccountId();
        Long from = mainShopId();
        Long to = newShop(account, "Filial-2");
        Product src = newProduct(from, "Coca-Cola 1L", "BC-CLONE-1", 10, "11020001001000000");

        TransferResponse resp = transfers.create(account, "kassir",
                new TransferRequest(from, to, src.getId(), new BigDecimal("3"), "ko'chirish"));

        // No ClassCastException, no missing-column SQL error → we get here.
        assertThat(quantityOf(src.getId())).isEqualTo(7);            // source decremented
        assertThat(resp.destProductId()).isNotNull();
        Product dest = products.findById(resp.destProductId()).orElseThrow();
        assertThat(dest.getShopId()).isEqualTo(to);
        assertThat(dest.getQuantity()).isEqualTo(3);                 // destination credited
        // mxik_code (NOT ikpu_code) copied to the cloned row.
        assertThat(dest.getMxikCode()).isEqualTo("11020001001000000");
        // Audit row written.
        assertThat(transferRows.findByAccountIdOrderByCreatedAtDesc(account))
                .anySatisfy(t -> assertThat(t.getProductBarcode()).isEqualTo("BC-CLONE-1"));
    }

    @Test
    void transferIntoExistingDestinationIncrementsStock() {
        Long account = seededAccountId();
        Long from = mainShopId();
        Long to = newShop(account, "Filial-merge");
        Product src = newProduct(from, "Fanta", "BC-MERGE-1", 8, null);
        Product existingDest = newProduct(to, "Fanta", "BC-MERGE-1", 5, null);

        transfers.create(account, "kassir",
                new TransferRequest(from, to, src.getId(), new BigDecimal("2"), null));

        assertThat(quantityOf(src.getId())).isEqualTo(6);            // 8 - 2
        assertThat(quantityOf(existingDest.getId())).isEqualTo(7);   // 5 + 2, no new row
    }

    @Test
    void insufficientStockIsRejected() {
        Long account = seededAccountId();
        Long from = mainShopId();
        Long to = newShop(account, "Filial-low");
        Product src = newProduct(from, "Sprite", "BC-LOW-1", 2, null);

        assertThatThrownBy(() -> transfers.create(account, "kassir",
                new TransferRequest(from, to, src.getId(), new BigDecimal("5"), null)))
                .isInstanceOf(BadRequestException.class);
        assertThat(quantityOf(src.getId())).isEqualTo(2);            // unchanged
    }

    @Test
    void fractionalQuantityIsRejected() {
        Long account = seededAccountId();
        Long from = mainShopId();
        Long to = newShop(account, "Filial-frac");
        Product src = newProduct(from, "Pepsi", "BC-FRAC-1", 10, null);

        assertThatThrownBy(() -> transfers.create(account, "kassir",
                new TransferRequest(from, to, src.getId(), new BigDecimal("1.5"), null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void sourceProductNotInFromShopIsRejected() {
        Long account = seededAccountId();
        Long from = mainShopId();
        Long to = newShop(account, "Filial-x");
        // Product actually lives in the destination shop, not the source shop.
        Product wrong = newProduct(to, "Misplaced", "BC-WRONG-1", 5, null);

        assertThatThrownBy(() -> transfers.create(account, "kassir",
                new TransferRequest(from, to, wrong.getId(), new BigDecimal("1"), null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void callerFromAnotherAccountCannotTransfer() {
        Long account = seededAccountId();
        Long from = mainShopId();
        Long to = newShop(account, "Filial-tenant");
        Product src = newProduct(from, "Cross-tenant", "BC-TEN-1", 5, null);

        // A different account must not be able to move this account's stock.
        Long foreignAccount = account + 9_999;
        assertThatThrownBy(() -> transfers.create(foreignAccount, "attacker",
                new TransferRequest(from, to, src.getId(), new BigDecimal("1"), null)))
                .isInstanceOf(NotFoundException.class);
        assertThat(quantityOf(src.getId())).isEqualTo(5);            // unchanged
    }
}
