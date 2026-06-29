package uz.barakat.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import uz.barakat.market.domain.Product;
import uz.barakat.market.dto.ProductImportResult;
import uz.barakat.market.repository.CategoryRepository;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.StockMovementRepository;
import uz.barakat.market.service.ProductImporter.ImportRow;
import uz.barakat.market.telegram.TelegramService;

/**
 * Audit fix: bulk import previously called {@code products.save()} directly,
 * bypassing the create endpoint's duplicate guard. It now routes every row
 * through the same {@code requireNoDuplicate} check, turning a clash into a
 * row-level error rather than an all-or-nothing abort or a silent duplicate.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductImportServiceTest {

    @Mock private ProductRepository products;
    @Mock private CategoryRepository categories;
    @Mock private StockMovementRepository movements;
    @Mock private CategoryService categoryService;
    @Mock private ProductImporter importer;
    @Mock private TelegramService telegram;
    @Mock private ApplicationEventPublisher events;
    @InjectMocks private ProductService service;

    private final MultipartFile file =
            new MockMultipartFile("file", "p.csv", "text/csv", new byte[]{1});

    private static ImportRow row(int line, String name, String barcode) {
        return new ImportRow(line, name, barcode, null, null,
                new BigDecimal("1.00"), new BigDecimal("2.00"), 5, 0, null, null);
    }

    private static ImportRow parserErrorRow(int line, String message) {
        return new ImportRow(line, null, null, null, null, null, null, 0, 0, null, message);
    }

    @Test
    void sameNameDifferentBarcodeRowsAreImported() {
        // New rule: product NAME is NOT unique per shop. Two rows that share a name
        // but have distinct barcodes are both imported — the barcode is the identity.
        when(importer.parse(file)).thenReturn(List.of(
                row(2, "Coca-Cola", "11111"), row(3, "Coca-Cola", "22222")));
        when(products.existsByBarcode(anyString())).thenReturn(false);

        ProductImportResult result = service.importProducts(file);

        verify(products, times(2)).save(any(Product.class));
        assertThat(result.importedCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isZero();
        // The duplicate guard must never consult the name anymore.
        verify(products, never()).existsByNameIgnoreCase(anyString());
    }

    @Test
    void duplicateBarcodeRowIsRejected() {
        when(importer.parse(file)).thenReturn(List.of(row(3, "Unique Name", "4780000000001")));
        when(products.existsByBarcode(anyString())).thenReturn(true);

        ProductImportResult result = service.importProducts(file);

        verify(products, never()).save(any(Product.class));
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("Qator 3"));
    }

    @Test
    void validRowsAreImported() {
        when(importer.parse(file)).thenReturn(List.of(row(2, "Alpha", null), row(3, "Beta", null)));

        ProductImportResult result = service.importProducts(file);

        verify(products, times(2)).save(any(Product.class));
        assertThat(result.importedCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isZero();
    }

    @Test
    void mixedFileImportsValidRowsAndReportsDuplicateBarcodeWithoutAborting() {
        when(importer.parse(file)).thenReturn(List.of(
                row(2, "Fresh", "55555"), row(3, "Existing", "99999")));
        when(products.existsByBarcode("55555")).thenReturn(false);
        when(products.existsByBarcode("99999")).thenReturn(true);

        ProductImportResult result = service.importProducts(file);

        // Valid row persisted; duplicate-BARCODE row skipped — no all-or-nothing abort.
        verify(products, times(1)).save(any(Product.class));
        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("Qator 3"));
    }

    @Test
    void parserErrorRowIsReportedNotSaved() {
        when(importer.parse(file)).thenReturn(List.of(parserErrorRow(4, "Narx noto'g'ri")));

        ProductImportResult result = service.importProducts(file);

        verify(products, never()).save(any(Product.class));
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("Qator 4"));
    }
}
