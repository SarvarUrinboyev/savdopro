package uz.barakat.market.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;
import uz.barakat.market.dto.BarcodeLookupResponse;
import uz.barakat.market.util.BarcodeNormalizer;

/**
 * Looks a barcode up in global product databases and returns a name + suggested
 * category for the warehouse "new product" form.
 *
 * <p>The Uzbek national catalogue still runs browser-side first. This service is
 * the fallback for international barcodes: food, electronics, cosmetics, pet
 * products, medicines and other retail items. It is read-only and best-effort:
 * external API failures degrade to found:false instead of blocking a scan.
 */
@Service
public class BarcodeLookupService {

    private static final Logger log = LoggerFactory.getLogger(BarcodeLookupService.class);
    private static final Pattern TITLE =
            Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern DESCRIPTION =
            Pattern.compile("(?is)<meta\\s+[^>]*name=[\"']description[\"'][^>]*content=[\"'](.*?)[\"']");

    private final RestClient restClient;
    private final String barcodeListUrl;
    private final String openFoodFactsUrl;
    private final String openProductsFactsUrl;
    private final String openBeautyFactsUrl;
    private final String openPetFoodFactsUrl;
    private final String googleBooksUrl;
    private final String openLibraryUrl;
    private final String upcItemDbUrl;
    private final String barcodeLookupUrl;
    private final String barcodeLookupKey;

    public BarcodeLookupService(
            RestClient barcodeLookupRestClient,
            @Value("${barakat.barcode.barcodelist.url}") String barcodeListUrl,
            @Value("${barakat.barcode.openfoodfacts.url}") String openFoodFactsUrl,
            @Value("${barakat.barcode.openproductsfacts.url}") String openProductsFactsUrl,
            @Value("${barakat.barcode.openbeautyfacts.url}") String openBeautyFactsUrl,
            @Value("${barakat.barcode.openpetfoodfacts.url}") String openPetFoodFactsUrl,
            @Value("${barakat.barcode.googlebooks.url}") String googleBooksUrl,
            @Value("${barakat.barcode.openlibrary.url}") String openLibraryUrl,
            @Value("${barakat.barcode.upcitemdb.url}") String upcItemDbUrl,
            @Value("${barakat.barcode.barcodelookup.url}") String barcodeLookupUrl,
            @Value("${barakat.barcode.barcodelookup.key:}") String barcodeLookupKey) {
        this.restClient = barcodeLookupRestClient;
        this.barcodeListUrl = barcodeListUrl;
        this.openFoodFactsUrl = openFoodFactsUrl;
        this.openProductsFactsUrl = openProductsFactsUrl;
        this.openBeautyFactsUrl = openBeautyFactsUrl;
        this.openPetFoodFactsUrl = openPetFoodFactsUrl;
        this.googleBooksUrl = googleBooksUrl;
        this.openLibraryUrl = openLibraryUrl;
        this.upcItemDbUrl = upcItemDbUrl;
        this.barcodeLookupUrl = barcodeLookupUrl;
        this.barcodeLookupKey = barcodeLookupKey == null ? "" : barcodeLookupKey.trim();
    }

    /**
     * Resolve a scanned code. Hits are cached for an hour; misses are not cached
     * so a temporary API outage can recover on the next scan.
     */
    @Cacheable(value = "barcodeLookup", key = "#code", unless = "!#result.found()")
    public BarcodeLookupResponse lookup(String code) {
        String canonical = BarcodeNormalizer.gtin(code);
        if (canonical == null) {
            return BarcodeLookupResponse.notFound();
        }

        // Russian/CIS products (GS1 prefix 460-469) are often absent from the
        // Open Facts databases but present in barcode-list.ru. Try it early so
        // the warehouse scanner still fills the form inside the client timeout.
        if (canonical.startsWith("46")) {
            BarcodeLookupResponse cis = parseBarcodeList(getHtml(barcodeListUrl, canonical));
            if (cis != null) {
                return cis;
            }
        }

        BarcodeLookupResponse off = parseOpenFacts(
                getJson(openFoodFactsUrl, canonical), "openfoodfacts", "Oziq-ovqat");
        if (off != null) {
            return off;
        }

        BarcodeLookupResponse opf = parseOpenFacts(
                getJson(openProductsFactsUrl, canonical), "openproductsfacts", null);
        if (opf != null) {
            return opf;
        }

        BarcodeLookupResponse beauty = parseOpenFacts(
                getJson(openBeautyFactsUrl, canonical), "openbeautyfacts", "Kosmetika");
        if (beauty != null) {
            return beauty;
        }

        BarcodeLookupResponse pet = parseOpenFacts(
                getJson(openPetFoodFactsUrl, canonical), "openpetfoodfacts", "Hayvonlar uchun");
        if (pet != null) {
            return pet;
        }

        if (isIsbn(canonical)) {
            BarcodeLookupResponse googleBook = parseGoogleBooks(getJson(googleBooksUrl, canonical));
            if (googleBook != null) {
                return googleBook;
            }

            BarcodeLookupResponse openLibraryBook = parseOpenLibrary(getJson(openLibraryUrl, canonical));
            if (openLibraryBook != null) {
                return openLibraryBook;
            }
        }

        BarcodeLookupResponse paidLookup = parseBarcodeLookup(getJsonWithKey(canonical));
        if (paidLookup != null) {
            return paidLookup;
        }

        BarcodeLookupResponse upc = parseUpcItemDb(getJson(upcItemDbUrl, canonical));
        if (upc != null) {
            return upc;
        }
        return BarcodeLookupResponse.notFound();
    }

    /** GETs a {barcode} URL template as HTML; null on any failure. */
    private String getHtml(String urlTemplate, String code) {
        try {
            return restClient.get()
                    .uri(urlTemplate, code)
                    .accept(MediaType.TEXT_HTML, MediaType.ALL)
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            log.debug("Barcode HTML lookup call failed ({}): {}", urlTemplate, ex.toString());
            return null;
        }
    }

    /** GETs a {barcode} URL template as JSON; null on any failure. */
    private JsonNode getJson(String urlTemplate, String code) {
        try {
            return restClient.get()
                    .uri(urlTemplate, code)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            log.debug("Barcode lookup call failed ({}): {}", urlTemplate, ex.toString());
            return null;
        }
    }

    /** GETs the optional paid lookup source; skipped when no API key is set. */
    private JsonNode getJsonWithKey(String code) {
        if (barcodeLookupKey.isBlank()) {
            return null;
        }
        try {
            return restClient.get()
                    .uri(barcodeLookupUrl, code, barcodeLookupKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            log.debug("BarcodeLookup call failed ({}): {}", barcodeLookupUrl, ex.toString());
            return null;
        }
    }

    /**
     * Open Food/Products/Beauty/Pet Facts:
     * { status:1, product:{ product_name(_uz), categories_tags:[...] } }.
     */
    private BarcodeLookupResponse parseOpenFacts(JsonNode root, String source, String fallbackCategory) {
        if (root == null || root.path("status").asInt(0) != 1) {
            return null;
        }
        JsonNode product = root.path("product");
        String name = firstNonBlank(
                text(product, "product_name_uz"),
                text(product, "product_name"),
                text(product, "product_name_en"),
                text(product, "generic_name_uz"),
                text(product, "generic_name"),
                text(product, "generic_name_en"));
        if (name == null) {
            return null;
        }
        String externalCategory = firstNonBlank(
                cleanTag(product.path("categories_tags")),
                cleanPath(text(product, "categories")),
                fallbackCategory);
        return new BarcodeLookupResponse(true, name, localCategory(externalCategory), source);
    }

    /** Optional BarcodeLookup-compatible response: { products:[ { title, category } ] }. */
    private BarcodeLookupResponse parseBarcodeLookup(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode products = root.path("products");
        if (!products.isArray() || products.isEmpty()) {
            return null;
        }
        JsonNode first = products.get(0);
        String name = firstNonBlank(
                text(first, "title"),
                text(first, "product_name"),
                text(first, "name"),
                text(first, "description"));
        if (name == null) {
            return null;
        }
        String category = firstNonBlank(
                cleanPath(text(first, "category")),
                cleanPath(text(first, "category_path")),
                lastFromArray(first.path("categoryPath")),
                lastFromArray(first.path("categories")));
        return new BarcodeLookupResponse(true, name, localCategory(category), "barcodelookup");
    }

    /** Google Books: { totalItems, items:[ { volumeInfo:{ title, authors, categories } } ] }. */
    private BarcodeLookupResponse parseGoogleBooks(JsonNode root) {
        if (root == null || root.path("totalItems").asInt(0) <= 0) {
            return null;
        }
        JsonNode items = root.path("items");
        if (!items.isArray() || items.isEmpty()) {
            return null;
        }
        JsonNode info = items.get(0).path("volumeInfo");
        String title = firstNonBlank(text(info, "title"), text(info, "subtitle"));
        if (title == null) {
            return null;
        }
        String author = firstFromArray(info.path("authors"));
        String name = author == null ? title : title + " - " + author;
        return new BarcodeLookupResponse(true, name, "Kitoblar", "googlebooks");
    }

    /** Open Library search: { numFound, docs:[ { title, author_name:[...] } ] }. */
    private BarcodeLookupResponse parseOpenLibrary(JsonNode root) {
        if (root == null || root.path("numFound").asInt(root.path("num_found").asInt(0)) <= 0) {
            return null;
        }
        JsonNode docs = root.path("docs");
        if (!docs.isArray() || docs.isEmpty()) {
            return null;
        }
        JsonNode doc = docs.get(0);
        String title = text(doc, "title");
        if (title == null) {
            return null;
        }
        String author = firstFromArray(doc.path("author_name"));
        String name = author == null ? title : title + " - " + author;
        return new BarcodeLookupResponse(true, name, "Kitoblar", "openlibrary");
    }

    /** UPC Item DB: { items:[ { title, category } ] }. */
    private BarcodeLookupResponse parseUpcItemDb(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode items = root.path("items");
        if (!items.isArray() || items.isEmpty()) {
            return null;
        }
        JsonNode first = items.get(0);
        String title = firstNonBlank(text(first, "title"), text(first, "description"));
        if (title == null) {
            return null;
        }
        return new BarcodeLookupResponse(true, title, localCategory(lastSegment(text(first, "category"))),
                "upcitemdb");
    }

    /**
     * barcode-list.ru HTML pages expose the most useful product name in either
     * the title ("Name - Shtrix-kod...") or the description's first product.
     */
    static BarcodeLookupResponse parseBarcodeList(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String title = firstMatch(TITLE, html);
        String name = cleanBarcodeListName(title);
        if (name == null) {
            String description = firstMatch(DESCRIPTION, html);
            name = cleanDescriptionProduct(description);
        }
        if (name == null) {
            return null;
        }
        return new BarcodeLookupResponse(true, name, inferCategoryFromName(name), "barcode-list");
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private static String firstMatch(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        return HtmlUtils.htmlUnescape(matcher.group(1)).trim();
    }

    private static String cleanBarcodeListName(String raw) {
        if (raw == null) {
            return null;
        }
        int marker = raw.indexOf(" - ");
        String name = marker > 0 ? raw.substring(0, marker) : raw;
        return normalProductName(name);
    }

    private static String cleanDescriptionProduct(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw;
        int colon = value.lastIndexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) {
            value = value.substring(0, semicolon);
        }
        return normalProductName(value);
    }

    private static String normalProductName(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (value.isEmpty()) {
            return null;
        }
        return value;
    }

    private static String inferCategoryFromName(String name) {
        if (name == null) {
            return null;
        }
        String n = name.toLowerCase();
        if (containsAny(n, "\u0437\u0443\u0431", "\u043f\u0430\u0441\u0442\u0430",
                "tooth", "splat", "\u0441\u043f\u043b\u0430\u0442",
                "\u0448\u0430\u043c\u043f\u0443\u043d", "shampoo",
                "\u043c\u044b\u043b\u043e", "soap", "\u0433\u0435\u043b\u044c",
                "\u043a\u0440\u0435\u043c", "cosmetic")) {
            return "Kosmetika";
        }
        if (containsAny(n, "\u043b\u0435\u043a\u0430\u0440",
                "\u0442\u0430\u0431\u043b\u0435\u0442",
                "\u043a\u0430\u043f\u0441\u0443\u043b",
                "\u0444\u0430\u0440\u043c\u0430", "medicine", "vitamin",
                "\u0432\u0438\u0442\u0430\u043c\u0438\u043d",
                "\u0441\u0438\u0440\u043e\u043f", "\u043c\u0430\u0437\u044c")) {
            return "Dori vositalari";
        }
        if (containsAny(n, "\u0447\u0430\u0439", "coffee", "\u043a\u043e\u0444\u0435",
                "water", "\u0432\u043e\u0434\u0430", "\u0441\u043e\u043a", "cola",
                "ichimlik")) {
            return "Ichimliklar";
        }
        if (containsAny(n, "\u043f\u0435\u0447\u0435\u043d\u044c\u0435",
                "\u043a\u043e\u043d\u0444\u0435\u0442", "chocolate",
                "\u0448\u043e\u043a\u043e\u043b\u0430\u0434", "candy", "sweet")) {
            return "Shirinliklar";
        }
        if (containsAny(n, "\u043a\u043e\u0440\u043c", "cat", "dog", "pet")) {
            return "Hayvonlar uchun";
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /** Keep the deepest/most specific Open Facts category tag. */
    private static String cleanTag(JsonNode tags) {
        if (!tags.isArray() || tags.isEmpty()) {
            return null;
        }
        for (int i = tags.size() - 1; i >= 0; i--) {
            String tag = cleanLabel(tags.get(i).asText(""));
            if (tag != null) {
                return tag;
            }
        }
        return null;
    }

    /** Cleans category paths such as "Food > Candy" or "Food, Candy". */
    private static String cleanPath(String category) {
        return cleanLabel(lastSegment(category));
    }

    private static String lastSegment(String category) {
        if (category == null) {
            return null;
        }
        String normalized = category.replace("/", ">").replace("\\", ">");
        int gt = normalized.lastIndexOf('>');
        int comma = normalized.lastIndexOf(',');
        int start = Math.max(gt, comma);
        String last = (start >= 0 ? normalized.substring(start + 1) : normalized).trim();
        return last.isEmpty() ? null : last;
    }

    private static String lastFromArray(JsonNode values) {
        if (!values.isArray() || values.isEmpty()) {
            return null;
        }
        for (int i = values.size() - 1; i >= 0; i--) {
            String label = cleanLabel(values.get(i).asText(""));
            if (label != null) {
                return label;
            }
        }
        return null;
    }

    private static String firstFromArray(JsonNode values) {
        if (!values.isArray() || values.isEmpty()) {
            return null;
        }
        for (JsonNode value : values) {
            String label = value.asText("").trim();
            if (!label.isEmpty()) {
                return label;
            }
        }
        return null;
    }

    private static String cleanLabel(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value
                .replaceFirst("^[a-z]{2}:", "")
                .replace('-', ' ')
                .replace('_', ' ')
                .trim()
                .replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return null;
        }
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    /**
     * Convert external category labels to local Uzbek buckets. If no mapping is
     * obvious, keep a cleaned version so the user can still create/edit it.
     */
    private static String localCategory(String externalCategory) {
        String label = cleanLabel(externalCategory);
        if (label == null) {
            return null;
        }
        String c = label.toLowerCase();
        if (containsAny(c, "beverage", "drink", "juice", "water", "soda", "cola", "tea", "coffee",
                "ichimlik")) {
            return "Ichimliklar";
        }
        if (containsAny(c, "candy", "sweet", "chocolate", "confection", "dessert", "shirinlik")) {
            return "Shirinliklar";
        }
        if (containsAny(c, "food", "grocery", "snack", "bakery", "bread", "dairy", "meat",
                "rice", "pasta", "sauce", "spice", "oil", "cereal", "biscuit", "cookie",
                "oziq ovqat")) {
            return "Oziq-ovqat";
        }
        if (containsAny(c, "phone", "smartphone", "cell phone", "mobile phone", "iphone", "android",
                "smartfon")) {
            return "Smartfonlar";
        }
        if (containsAny(c, "electronic", "computer", "camera", "audio", "video", "appliance",
                "charger", "cable", "headphone", "elektron")) {
            return "Elektronika";
        }
        if (containsAny(c, "medicine", "medication", "pharma", "drug", "pharmacy", "supplement",
                "vitamin", "medical", "dori")) {
            return "Dori vositalari";
        }
        if (containsAny(c, "cosmetic", "beauty", "skin care", "personal care", "makeup",
                "perfume", "shampoo", "soap", "kosmetik")) {
            return "Kosmetika";
        }
        if (containsAny(c, "pet", "dog", "cat", "animal", "hayvon")) {
            return "Hayvonlar uchun";
        }
        if (containsAny(c, "baby", "kids", "children", "toy", "bolalar")) {
            return "Bolalar mahsulotlari";
        }
        if (containsAny(c, "book", "office", "stationery", "kanselyariya")) {
            return "Kanselyariya";
        }
        if (containsAny(c, "tobacco", "cigarette", "tamaki")) {
            return "Tamaki mahsulotlari";
        }
        return label;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIsbn(String code) {
        return code != null && code.length() == 13
                && (code.startsWith("978") || code.startsWith("979"));
    }
}
