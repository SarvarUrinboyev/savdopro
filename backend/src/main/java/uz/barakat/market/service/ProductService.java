package uz.barakat.market.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.barakat.market.domain.Category;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.StockMovement;
import uz.barakat.market.domain.StockReason;
import uz.barakat.market.dto.ProductImportResult;
import uz.barakat.market.dto.ProductRequest;
import uz.barakat.market.dto.ProductResponse;
import uz.barakat.market.dto.ScanResponse;
import uz.barakat.market.dto.StockAdjustRequest;
import uz.barakat.market.dto.StockMovementResponse;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.CategoryRepository;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.StockMovementRepository;
import uz.barakat.market.telegram.TelegramService;

/** Warehouse / inventory: products, stock movements and bulk import. */
@Service
@Transactional
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final StockMovementRepository movements;
    private final CategoryService categoryService;
    private final ProductImporter importer;
    private final TelegramService telegram;

    public ProductService(ProductRepository products, CategoryRepository categories,
                          StockMovementRepository movements, CategoryService categoryService,
                          ProductImporter importer, TelegramService telegram) {
        this.products = products;
        this.categories = categories;
        this.movements = movements;
        this.categoryService = categoryService;
        this.importer = importer;
        this.telegram = telegram;
    }

    /** Filtered list: free-text search (name/SKU), category and stock status. */
    @Transactional(readOnly = true)
    public List<ProductResponse> list(String search, Long categoryId, String status) {
        Map<Long, String> names = categoryNames();
        String query = search == null ? "" : search.strip().toLowerCase();
        return products.findAllByOrderByNameAsc().stream()
                .filter(p -> query.isEmpty()
                        || p.getName().toLowerCase().contains(query)
                        || (p.getBarcode() != null && p.getBarcode().toLowerCase().contains(query))
                        || (p.getImei1() != null && p.getImei1().toLowerCase().contains(query))
                        || (p.getImei2() != null && p.getImei2().toLowerCase().contains(query)))
                .filter(p -> categoryId == null || categoryId.equals(p.getCategoryId()))
                .filter(p -> status == null || status.isBlank()
                        || status.equals(Mappers.stockStatus(p)))
                .map(p -> Mappers.product(p, names.get(p.getCategoryId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        Product product = find(id);
        return Mappers.product(product, categoryName(product.getCategoryId()));
    }

    /** Products at or below their configured low-stock threshold. */
    @Transactional(readOnly = true)
    public List<ProductResponse> lowStock() {
        Map<Long, String> names = categoryNames();
        return products.findLowStockProducts().stream()
                .map(p -> Mappers.product(p, names.get(p.getCategoryId())))
                .toList();
    }

    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        applyFields(product, request);
        int initial = request.quantity() != null ? request.quantity() : 0;
        product.setQuantity(initial);
        products.save(product);
        if (initial > 0) {
            logMovement(product, initial, initial, StockReason.INITIAL, "Boshlang'ich qoldiq");
        }
        return Mappers.product(product, categoryName(product.getCategoryId()));
    }

    /** Updates product details. Stock quantity is changed via {@link #adjustStock}. */
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = find(id);
        applyFields(product, request);
        products.save(product);
        return Mappers.product(product, categoryName(product.getCategoryId()));
    }

    public void delete(Long id) {
        products.delete(find(id));
    }

    /** Kirim / Chiqim: changes the stock and records a movement with its reason. */
    public ProductResponse adjustStock(Long id, StockAdjustRequest request) {
        Product product = find(id);
        if (request.delta() == 0) {
            throw new BadRequestException("Miqdor noldan farqli bo'lishi kerak");
        }
        int before = product.getQuantity();
        int updated = before + request.delta();
        if (updated < 0) {
            throw new BadRequestException(
                    "Qoldiq manfiy bo'la olmaydi (hozir: " + before + " dona)");
        }
        product.setQuantity(updated);
        products.save(product);
        logMovement(product, request.delta(), updated, request.reason(), request.note());
        // Telegram alert when the stock dips below the configured
        // threshold AND it wasn't already low before this adjustment.
        // Without the "before" guard every subsequent sale of an
        // already-low product would re-spam the owner.
        maybeAlertLowStock(product, before);
        return Mappers.product(product, categoryName(product.getCategoryId()));
    }

    /** Fires only on a fresh in→below-threshold transition. */
    private void maybeAlertLowStock(Product product, int qtyBefore) {
        int threshold = product.getLowStockThreshold();
        if (threshold <= 0) return;
        int qtyNow = product.getQuantity();
        if (qtyNow >= threshold) return;
        if (qtyBefore < threshold) return;   // was already low — don't spam
        try {
            String emoji = qtyNow == 0 ? "🚨" : "⚠️";
            telegram.sendMessage(emoji + " Tovar zaxirasi kam qoldi"
                    + "\n\n" + product.getName()
                    + "\nMavjud: " + qtyNow + " " + safeUnit(product.getUnit())
                    + "\nMinimum: " + threshold
                    + (product.getBarcode() == null ? ""
                            : "\nBarcode: " + product.getBarcode())
                    + "\n\nTo'ldirishni unutmang.");
        } catch (RuntimeException ex) {
            // Never let an alert failure block the underlying stock change.
            log.warn("Low-stock alert failed for product {}: {}",
                    product.getId(), ex.toString());
        }
    }

    private static String safeUnit(String u) {
        return u == null || u.isBlank() ? "dona" : u;
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> movements(Long id) {
        find(id);
        return movements.findTop20ByProductIdOrderByIdDesc(id).stream()
                .map(Mappers::stockMovement)
                .toList();
    }

    /**
     * Scans a barcode: an existing product gets +1 stock (a DELIVERY
     * movement is logged); an unknown barcode is reported back so the UI
     * can offer to create a new product.
     */
    public ScanResponse scan(String barcode) {
        String code = barcode == null ? "" : barcode.strip();
        if (code.isEmpty()) {
            throw new BadRequestException("Shtrix kod bo'sh");
        }
        Product product = products.findFirstByBarcode(code).orElse(null);
        if (product == null) {
            return new ScanResponse(false, code, null);
        }
        product.setQuantity(product.getQuantity() + 1);
        products.save(product);
        logMovement(product, 1, product.getQuantity(), StockReason.DELIVERY, "Skaner orqali");
        return new ScanResponse(true, code,
                Mappers.product(product, categoryName(product.getCategoryId())));
    }

    /** Bulk import of products from a CSV or XLSX file. */
    public ProductImportResult importProducts(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Fayl tanlanmadi");
        }
        List<ProductImporter.ImportRow> rows = importer.parse(file);
        List<String> errors = new ArrayList<>();
        int imported = 0;
        for (ProductImporter.ImportRow row : rows) {
            if (row.error() != null) {
                errors.add("Qator " + row.line() + ": " + row.error());
                continue;
            }
            Product product = new Product();
            product.setName(row.name());
            product.setBarcode(row.barcode());
            product.setImei1(row.imei1());
            product.setImei2(row.imei2());
            product.setPurchasePrice(row.purchasePrice());
            product.setSalePrice(row.salePrice());
            product.setQuantity(row.quantity());
            product.setLowStockThreshold(row.lowStockThreshold());
            if (row.category() != null) {
                product.setCategoryId(categoryService.resolveOrCreate(row.category()));
            }
            products.save(product);
            if (row.quantity() > 0) {
                logMovement(product, row.quantity(), row.quantity(),
                        StockReason.INITIAL, "Import (fayldan)");
            }
            imported++;
        }
        return new ProductImportResult(imported, errors.size(), errors);
    }

    // --------------------------------------------------------------- helpers

    private void applyFields(Product product, ProductRequest request) {
        if (request.categoryId() != null && !categories.existsById(request.categoryId())) {
            throw new BadRequestException("Tanlangan toifa topilmadi");
        }
        product.setName(request.name().strip());
        product.setBarcode(blankToNull(request.barcode()));
        product.setImei1(blankToNull(request.imei1()));
        product.setImei2(blankToNull(request.imei2()));
        product.setPurchasePrice(request.purchasePrice());
        product.setSalePrice(request.salePrice());
        product.setCategoryId(request.categoryId());
        product.setDescription(blankToNull(request.description()));
        product.setLowStockThreshold(
                request.lowStockThreshold() != null ? request.lowStockThreshold() : 0);
        product.setMxikCode(blankToNull(request.mxikCode()));
        product.setVatRate(request.vatRate());
        String unit = blankToNull(request.unit());
        product.setUnit(unit != null ? unit : "dona");
    }

    private void logMovement(Product product, int delta, int resulting,
                             StockReason reason, String note) {
        StockMovement movement = new StockMovement();
        movement.setProductId(product.getId());
        movement.setDelta(delta);
        movement.setResultingQuantity(resulting);
        movement.setReason(reason);
        movement.setNote(note);
        // Freeze the product's price so historical profit reports value this
        // movement at the price as it was, not the current one.
        movement.setUnitSalePrice(product.getSalePrice());
        movement.setUnitCostPrice(product.getPurchasePrice());
        movements.save(movement);
    }

    private Map<Long, String> categoryNames() {
        return categories.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categories.findById(categoryId).map(Category::getName).orElse(null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Product find(Long id) {
        return products.findById(id).orElseThrow(() -> NotFoundException.of("Mahsulot", id));
    }
}
