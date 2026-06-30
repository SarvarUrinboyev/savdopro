package uz.barakat.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.StockMovement;
import uz.barakat.market.domain.StockReason;
import uz.barakat.market.dto.ProductRequest;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.repository.CategoryRepository;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.StockMovementRepository;
import uz.barakat.market.telegram.TelegramService;

/**
 * Unit tests for the atomic create-and-stock path of {@link ProductService}.
 *
 * <p>Creating a product also records its opening {@code INITIAL} stock movement;
 * both writes must happen in one transaction so a failure recording the movement
 * leaves NO orphaned product behind. This is the create endpoint the warehouse
 * scan modal reuses (rather than a separate scan-create), so its atomicity is
 * what the global-barcode-lookup save flow relies on.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceTest {

    @Mock private ProductRepository products;
    @Mock private CategoryRepository categories;
    @Mock private StockMovementRepository movements;
    @Mock private CategoryService categoryService;
    @Mock private ProductImporter importer;
    @Mock private TelegramService telegram;
    @Mock private ApplicationEventPublisher events;
    @InjectMocks private ProductService service;

    /** A minimal valid create payload — uncategorised, with the given opening stock. */
    private static ProductRequest newProduct(Integer quantity) {
        return new ProductRequest(
                "Coca-Cola 0.5L", null, null, null,
                new BigDecimal("1.00"), new BigDecimal("2.00"),
                quantity, null, null, null, null, null, null, null, null, false);
    }

    @Test
    void createWritesProductThenInitialStockMovementInOneUnit() {
        service.create(newProduct(24));

        // The product is persisted first, then its opening movement — one unit of work.
        InOrder order = inOrder(products, movements);
        order.verify(products).save(any(Product.class));
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        order.verify(movements).save(captor.capture());
        StockMovement movement = captor.getValue();
        assertEquals(StockReason.INITIAL, movement.getReason());
        assertEquals(24, movement.getDelta());
        assertEquals(24, movement.getResultingQuantity());
    }

    @Test
    void createWithZeroOpeningStockRecordsNoMovement() {
        service.create(newProduct(0));

        verify(products).save(any(Product.class));
        verify(movements, never()).save(any());
    }

    @Test
    void movementFailureRollsBackTheProductCreate() {
        when(movements.save(any(StockMovement.class)))
                .thenThrow(new RuntimeException("stock_movements insert failed"));

        // create() must let the failure propagate: that is what makes the
        // surrounding @Transactional roll back the already-issued products.save,
        // so no orphaned product is ever committed.
        assertThrows(RuntimeException.class, () -> service.create(newProduct(24)));
        verify(products).save(any(Product.class));   // save was attempted, then rolled back
    }

    @Test
    void createRunsInsideATransactionSoBothWritesCommitOrRollBackTogether() {
        // The atomicity the rollback relies on comes from the class-level
        // @Transactional; assert it's present so a refactor can't silently split
        // the product and stock-movement writes into separate transactions.
        assertTrue(ProductService.class.isAnnotationPresent(Transactional.class),
                "ProductService must be @Transactional for create() to be atomic");
    }

    // ---- product uniqueness rule: identity is the BARCODE, name may repeat ----

    /** A create/update payload with a given name + barcode (uncategorised, stock 1). */
    private static ProductRequest withBarcode(String name, String barcode) {
        return new ProductRequest(name, barcode, null, null,
                new BigDecimal("1.00"), new BigDecimal("2.00"),
                1, null, null, null, null, null, null, null, null, false);
    }

    @Test
    void createAllowsDuplicateNameAndNeverChecksNameUniqueness() {
        // Same display name as an existing product is fine — only the barcode is
        // the identity (existsByBarcode = false => create proceeds).
        when(products.existsByBarcode(any())).thenReturn(false);

        service.create(withBarcode("Coca-Cola", "10001"));

        verify(products).save(any(Product.class));
        verify(products, never()).existsByNameIgnoreCase(any());
    }

    @Test
    void createRejectsDuplicateBarcode() {
        when(products.existsByBarcode(any())).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.create(withBarcode("Anything", "10001")));
        assertTrue(ex.getMessage().contains("shtrix-kod"));
        verify(products, never()).save(any(Product.class));
    }

    @Test
    void updateRejectsDuplicateBarcodeButNeverChecksDuplicateName() {
        Product existing = new Product();
        existing.setName("Old name");
        when(products.findById(7L)).thenReturn(java.util.Optional.of(existing));
        when(products.existsByBarcodeAndIdNot(any(), eq(7L))).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> service.update(7L, withBarcode("Some Other Name", "10001")));
        verify(products, never()).existsByNameIgnoreCaseAndIdNot(any(), any());
    }

    @Test
    void createWithoutBarcodeStillRejectsDuplicateName() {
        // PHASE B.4: a barcode-less product keeps the name guard, so two
        // indistinguishable code-less products can't collide.
        when(products.existsByNameIgnoreCase("Coca-Cola")).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.create(withBarcode("Coca-Cola", null)));
        assertTrue(ex.getMessage().contains("nomli"));
        verify(products, never()).save(any(Product.class));
    }
}
