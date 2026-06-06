package uz.barakat.market.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import uz.barakat.market.dto.BarcodeLookupResponse;

/**
 * Looks a barcode up in GLOBAL product databases and returns a name + suggested
 * category for the warehouse "new product" form.
 *
 * <p>Unlike the Uzbek national catalogue (tasnif.soliq.uz) — which the browser
 * queries directly because the hosted backend can't reach Uzbek government
 * endpoints — Open Food Facts and UPC Item DB are international services the
 * backend CAN reach, so this runs server-side. The scan modal calls it only as
 * a fallback, when the national catalogue has nothing.
 *
 * <p>It is strictly read-only: it suggests, it never creates. Every external
 * call is best-effort with a hard timeout (see {@code barcodeLookupRestClient})
 * and any failure degrades to {@code found:false} — a slow or unreachable API
 * must never block a scan.
 */
@Service
public class BarcodeLookupService {

    private static final Logger log = LoggerFactory.getLogger(BarcodeLookupService.class);

    private final RestClient restClient;
    private final String openFoodFactsUrl;
    private final String upcItemDbUrl;

    public BarcodeLookupService(
            RestClient barcodeLookupRestClient,
            @Value("${barakat.barcode.openfoodfacts.url}") String openFoodFactsUrl,
            @Value("${barakat.barcode.upcitemdb.url}") String upcItemDbUrl) {
        this.restClient = barcodeLookupRestClient;
        this.openFoodFactsUrl = openFoodFactsUrl;
        this.upcItemDbUrl = upcItemDbUrl;
    }

    /**
     * Resolve a code: try Open Food Facts first, then UPC Item DB, stopping at
     * the first hit; {@code found:false} if neither knows it.
     *
     * <p>Hits are cached for an hour (key = the code) so repeated scans of the
     * same product don't re-hit the network. Misses are deliberately NOT cached
     * ({@code unless}) so a transient outage can be retried on the next scan.
     */
    @Cacheable(value = "barcodeLookup", key = "#code", unless = "!#result.found()")
    public BarcodeLookupResponse lookup(String code) {
        String canonical = code == null ? "" : code.trim();
        if (canonical.isEmpty()) {
            return BarcodeLookupResponse.notFound();
        }
        BarcodeLookupResponse off = parseOpenFoodFacts(getJson(openFoodFactsUrl, canonical));
        if (off != null) {
            return off;
        }
        BarcodeLookupResponse upc = parseUpcItemDb(getJson(upcItemDbUrl, canonical));
        if (upc != null) {
            return upc;
        }
        return BarcodeLookupResponse.notFound();
    }

    /** GETs a {@code {barcode}} URL template as JSON; null on ANY failure (timeout, 4xx/5xx, parse). */
    private JsonNode getJson(String urlTemplate, String code) {
        try {
            return restClient.get()
                    .uri(urlTemplate, code)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            // A slow / unreachable / erroring external API must never surface to
            // the caller — degrade to "not found" and let the cashier type it.
            log.debug("Barcode lookup call failed ({}): {}", urlTemplate, ex.toString());
            return null;
        }
    }

    /** Open Food Facts: {@code { status:1, product:{ product_name(_uz), categories_tags:[...] } }}. */
    private BarcodeLookupResponse parseOpenFoodFacts(JsonNode root) {
        if (root == null || root.path("status").asInt(0) != 1) {
            return null;   // status 0 = product not found
        }
        JsonNode product = root.path("product");
        String name = firstNonBlank(
                text(product, "product_name_uz"),
                text(product, "product_name"));
        if (name == null) {
            return null;
        }
        return new BarcodeLookupResponse(true, name, cleanTag(product.path("categories_tags")),
                "openfoodfacts");
    }

    /** UPC Item DB: {@code { items:[ { title, category } ] }}. */
    private BarcodeLookupResponse parseUpcItemDb(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode items = root.path("items");
        if (!items.isArray() || items.isEmpty()) {
            return null;
        }
        JsonNode first = items.get(0);
        String title = text(first, "title");
        if (title == null) {
            return null;
        }
        return new BarcodeLookupResponse(true, title, lastSegment(text(first, "category")),
                "upcitemdb");
    }

    // -------------------------------------------------------------- parsing helpers

    /** Trimmed text of {@code node.field}, or null when missing / blank. */
    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * The first Open Food Facts category tag, tidied: drop the language prefix
     * ("en:beverages" → "beverages"), turn separators into spaces and sentence-
     * case it ("en:plant-based-foods" → "Plant based foods").
     */
    private static String cleanTag(JsonNode tags) {
        if (!tags.isArray() || tags.isEmpty()) {
            return null;
        }
        String tag = tags.get(0).asText("")
                .replaceFirst("^[a-z]{2}:", "")
                .replace('-', ' ')
                .replace('_', ' ')
                .trim();
        if (tag.isEmpty()) {
            return null;
        }
        return Character.toUpperCase(tag.charAt(0)) + tag.substring(1);
    }

    /** UPC categories are {@code "A > B > C"} paths; keep the last, most specific segment. */
    private static String lastSegment(String category) {
        if (category == null) {
            return null;
        }
        int gt = category.lastIndexOf('>');
        String last = (gt >= 0 ? category.substring(gt + 1) : category).trim();
        return last.isEmpty() ? null : last;
    }
}
