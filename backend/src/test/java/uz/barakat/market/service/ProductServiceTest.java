package uz.barakat.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.StockMovement;
import uz.barakat.market.domain.StockReason;
import uz.barakat.market.dto.ProductRequest;
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
    @InjectMocks private ProductService service;

    /** A minimal valid create payload — uncategorised, with the given opening stock. */
    private static ProductRequest newProduct(Integer quantity) {
        return new ProductRequest(
                "Coca-Cola 0.5L", null, null, null,
                new BigDecimal("1.00"), new BigDecimal("2.00"),
                quantity, null, null, null, null, null, null, null, null);
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
}
