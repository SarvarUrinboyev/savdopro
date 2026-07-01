package uz.barakat.mobile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.mobile.domain.Category;
import uz.barakat.mobile.domain.Product;
import uz.barakat.mobile.repository.CategoryRepository;
import uz.barakat.mobile.repository.ProductRepository;

/**
 * POS → mobil katalog sinxroni. SavdoPRO backend'ining Public API'sidan
 * ({@code GET /api/v1/products}, X-Api-Key bilan) mahsulotlarni tortib,
 * mobil katalogga upsert qiladi — shunda mobil ilovadagi nom / narx /
 * qoldiq har doim POS ombori bilan bir xil yuradi.
 *
 * <p>Qoidalar:
 * <ul>
 *   <li>Upsert kaliti — {@code sourceProductId} (POS'dagi id). Qo'lda
 *       kiritilgan mahsulotlarga (sourceProductId=null) TEGILMAYDI.</li>
 *   <li>Narx: POS USD da yuritadi, mobil so'mda — {@code uzs-per-usd}
 *       kursi × {@code markup} bilan hisoblanadi, 100 so'mga yaxlitlanadi.</li>
 *   <li>Rasm/tavsif/kategoriya mobil tomonda kuratsiya qilinadi: mavjud
 *       mahsulotda ular saqlanadi; yangi mahsulot "POS import" kategoriyaga
 *       tushadi (keyin qo'lda ko'chirsa bo'ladi).</li>
 *   <li>Bu ro'yxatdan chiqib ketgan (POS'da o'chirilgan) sinxron mahsulot
 *       {@code active=false} qilinadi — hech qachon o'chirilmaydi.</li>
 *   <li>{@code CATALOG_SYNC_ENABLED} yoqilmaguncha to'liq no-op.</li>
 * </ul>
 */
@Service
public class CatalogSyncService {

    private static final Logger log = LoggerFactory.getLogger(CatalogSyncService.class);
    private static final String IMPORT_CATEGORY_SLUG = "pos-import";
    private static final int PAGE_SIZE = 200;

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final BigDecimal uzsPerUsd;
    private final BigDecimal markup;

    public CatalogSyncService(
            ProductRepository products, CategoryRepository categories,
            @Value("${sync.enabled:${CATALOG_SYNC_ENABLED:false}}") boolean enabled,
            @Value("${sync.base-url:${CATALOG_SYNC_BASE_URL:}}") String baseUrl,
            @Value("${sync.api-key:${CATALOG_SYNC_API_KEY:}}") String apiKey,
            @Value("${sync.uzs-per-usd:${CATALOG_SYNC_UZS_PER_USD:12800}}") BigDecimal uzsPerUsd,
            @Value("${sync.markup:${CATALOG_SYNC_MARKUP:1.0}}") BigDecimal markup) {
        this.products = products;
        this.categories = categories;
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.uzsPerUsd = uzsPerUsd;
        this.markup = markup;
    }

    /** Har 30 daqiqada (sozlanadi) + xohlasa qo'lda chaqiriladi. */
    @Scheduled(cron = "${sync.cron:0 */30 * * * *}")
    public void scheduledSync() {
        if (!enabled) {
            return;
        }
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            log.warn("Catalog sync yoqilgan, lekin CATALOG_SYNC_BASE_URL / "
                    + "CATALOG_SYNC_API_KEY berilmagan — o'tkazib yuborildi.");
            return;
        }
        try {
            SyncResult r = syncOnce();
            log.info("Catalog sync: {} yangi, {} yangilandi, {} o'chirildi (deactivate).",
                    r.created(), r.updated(), r.deactivated());
        } catch (Exception ex) {
            log.error("Catalog sync xato: {}", ex.toString());
        }
    }

    public record SyncResult(int created, int updated, int deactivated) { }

    @Transactional
    public SyncResult syncOnce() throws Exception {
        Category importCategory = ensureImportCategory();
        Set<Long> seen = new HashSet<>();
        int created = 0;
        int updated = 0;

        for (int page = 0; ; page++) {
            JsonNode arr = fetchPage(page);
            if (arr == null || !arr.isArray() || arr.isEmpty()) {
                break;
            }
            for (JsonNode n : arr) {
                long sourceId = n.path("id").asLong();
                if (sourceId <= 0) continue;
                seen.add(sourceId);
                Product p = products.findBySourceProductId(sourceId).orElse(null);
                boolean fresh = p == null;
                if (fresh) {
                    p = new Product();
                    p.setSourceProductId(sourceId);
                    p.setCategory(importCategory);
                    p.setPopular(false);
                }
                p.setName(n.path("name").asText(p.getName()));
                p.setBarcode(n.path("barcode").isNull() ? p.getBarcode()
                        : n.path("barcode").asText(null));
                p.setUnit(n.path("unit").asText("dona"));
                p.setStockQty(Math.max(0, n.path("stockQty").asInt(0)));
                p.setPrice(usdToSom(n.path("price").decimalValue()));
                p.setActive(n.path("available").asBoolean(p.getStockQty() > 0));
                products.save(p);
                if (fresh) created++; else updated++;
            }
            if (arr.size() < PAGE_SIZE) {
                break; // oxirgi sahifa
            }
        }

        // POS ro'yxatidan chiqqan sinxron mahsulotlar sotuvdan olinadi.
        int deactivated = 0;
        for (Product p : products.findBySourceProductIdIsNotNull()) {
            if (!seen.contains(p.getSourceProductId()) && p.isActive()) {
                p.setActive(false);
                products.save(p);
                deactivated++;
            }
        }
        return new SyncResult(created, updated, deactivated);
    }

    /** USD → so'm: kurs × markup, 100 so'mga yaxlitlash (kassadagi odat). */
    long usdToSom(BigDecimal usd) {
        if (usd == null) {
            return 0;
        }
        BigDecimal som = usd.multiply(uzsPerUsd).multiply(markup);
        return som.divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact() * 100;
    }

    private JsonNode fetchPage(int page) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/products?page=" + page + "&size=" + PAGE_SIZE))
                .header("X-Api-Key", apiKey)
                .timeout(Duration.ofSeconds(30))
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Public API " + resp.statusCode()
                    + " qaytardi (URL/kalit/scope'ni tekshiring)");
        }
        return json.readTree(resp.body());
    }

    private Category ensureImportCategory() {
        return categories.findAll().stream()
                .filter(c -> IMPORT_CATEGORY_SLUG.equals(c.getSlug()))
                .findFirst()
                .orElseGet(() -> {
                    Category c = new Category();
                    c.setName("POS import");
                    c.setSlug(IMPORT_CATEGORY_SLUG);
                    c.setSortOrder(999);
                    return categories.save(c);
                });
    }
}
