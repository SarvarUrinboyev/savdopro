package uz.barakat.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uz.barakat.market.dto.BarcodeLookupResponse;

/**
 * Unit tests for the global barcode lookup fall-through chain and never-throw
 * contract. HTTP calls are mocked; no real network is touched.
 */
class BarcodeLookupServiceTest {

    private static final String OFF_URL = "https://off.test/api/v2/product/{barcode}.json";
    private static final String OPF_URL = "https://opf.test/api/v2/product/{barcode}.json";
    private static final String OBF_URL = "https://obf.test/api/v2/product/{barcode}.json";
    private static final String PET_URL = "https://pet.test/api/v2/product/{barcode}.json";
    private static final String BARCODE_LIST_URL =
            "https://barcode-list.test/barcode/RU/barcode-{barcode}/search.htm";
    private static final String GOOGLE_BOOKS_URL = "https://books.test/volumes?q=isbn:{barcode}";
    private static final String OPEN_LIBRARY_URL = "https://ol.test/search.json?isbn={barcode}";
    private static final String UPC_URL = "https://upc.test/prod/trial/lookup?upc={barcode}";
    private static final String LOOKUP_URL =
            "https://lookup.test/v3/products?barcode={barcode}&formatted=y&key={key}";

    private static final String CODE = "4870204010023";
    private static final String OFF_REQUEST = "https://off.test/api/v2/product/" + CODE + ".json";
    private static final String OPF_REQUEST = "https://opf.test/api/v2/product/" + CODE + ".json";
    private static final String OBF_REQUEST = "https://obf.test/api/v2/product/" + CODE + ".json";
    private static final String PET_REQUEST = "https://pet.test/api/v2/product/" + CODE + ".json";
    private static final String UPC_REQUEST = "https://upc.test/prod/trial/lookup?upc=" + CODE;
    private static final String LOOKUP_REQUEST =
            "https://lookup.test/v3/products?barcode=" + CODE + "&formatted=y&key=secret";
    private static final String ISBN = "9781234567897";
    private static final String ISBN_OFF_REQUEST = "https://off.test/api/v2/product/" + ISBN + ".json";
    private static final String ISBN_OPF_REQUEST = "https://opf.test/api/v2/product/" + ISBN + ".json";
    private static final String ISBN_OBF_REQUEST = "https://obf.test/api/v2/product/" + ISBN + ".json";
    private static final String ISBN_PET_REQUEST = "https://pet.test/api/v2/product/" + ISBN + ".json";
    private static final String GOOGLE_BOOKS_REQUEST = "https://books.test/volumes?q=isbn:" + ISBN;
    private static final String CIS_CODE = "4603014022240";
    private static final String CIS_REQUEST =
            "https://barcode-list.test/barcode/RU/barcode-" + CIS_CODE + "/search.htm";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private BarcodeLookupService service;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = serviceWithKey("");
    }

    @Test
    void openFoodFactsHitReturnsNameAndMappedCategoryWithoutCallingLaterSources() {
        server.expect(requestTo(OFF_REQUEST)).andRespond(withSuccess("""
                { "status": 1, "product": {
                    "product_name": "Coca-Cola 0.5L",
                    "categories_tags": ["en:beverages", "en:sodas"] } }
                """, MediaType.APPLICATION_JSON));

        BarcodeLookupResponse result = service.lookup(CODE);

        assertTrue(result.found());
        assertEquals("Coca-Cola 0.5L", result.name());
        assertEquals("Ichimliklar", result.suggestedCategory());
        assertEquals("openfoodfacts", result.source());
        server.verify();
    }

    @Test
    void prefersUzbekProductNameWhenPresentAndFallsBackToFoodCategory() {
        server.expect(requestTo(OFF_REQUEST)).andRespond(withSuccess("""
                { "status": 1, "product": {
                    "product_name": "Cola",
                    "product_name_uz": "Kola 0.5L",
                    "categories_tags": [] } }
                """, MediaType.APPLICATION_JSON));

        BarcodeLookupResponse result = service.lookup(CODE);

        assertEquals("Kola 0.5L", result.name());
        assertEquals("Oziq-ovqat", result.suggestedCategory());
    }

    @Test
    void openProductsFactsHitCoversGeneralGoodsBeforeUpc() {
        server.expect(requestTo(OFF_REQUEST)).andRespond(withSuccess("{ \"status\": 0 }",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(OPF_REQUEST)).andRespond(withSuccess("""
                { "status": 1, "product": {
                    "product_name": "Galaxy USB-C Charger",
                    "categories_tags": ["en:electronics", "en:chargers"] } }
                """, MediaType.APPLICATION_JSON));

        BarcodeLookupResponse result = service.lookup(CODE);

        assertTrue(result.found());
        assertEquals("Galaxy USB-C Charger", result.name());
        assertEquals("Elektronika", result.suggestedCategory());
        assertEquals("openproductsfacts", result.source());
    }

    @Test
    void fallsThroughToUpcItemDbWhenOpenSourcesMiss() {
        expectOpenFactsMisses();
        server.expect(requestTo(UPC_REQUEST)).andRespond(withSuccess("""
                { "code": "OK", "total": 1, "items": [
                    { "title": "Snickers Bar 50g",
                      "category": "Food, Beverages & Tobacco > Food Items > Candy" } ] }
                """, MediaType.APPLICATION_JSON));

        BarcodeLookupResponse result = service.lookup(CODE);

        assertTrue(result.found());
        assertEquals("Snickers Bar 50g", result.name());
        assertEquals("Shirinliklar", result.suggestedCategory());
        assertEquals("upcitemdb", result.source());
    }

    @Test
    void optionalBarcodeLookupSourceIsUsedOnlyWhenApiKeyExists() {
        service = serviceWithKey("secret");
        expectOpenFactsMisses();
        server.expect(requestTo(LOOKUP_REQUEST)).andRespond(withSuccess("""
                { "products": [
                    { "title": "iPhone 15 Pro",
                      "category": "Electronics > Mobile Phones" } ] }
                """, MediaType.APPLICATION_JSON));

        BarcodeLookupResponse result = service.lookup(CODE);

        assertTrue(result.found());
        assertEquals("iPhone 15 Pro", result.name());
        assertEquals("Smartfonlar", result.suggestedCategory());
        assertEquals("barcodelookup", result.source());
    }

    @Test
    void isbnCodesUseGoogleBooksBeforePaidAndUpcSources() {
        expectOpenFactsMisses(ISBN_OFF_REQUEST, ISBN_OPF_REQUEST, ISBN_OBF_REQUEST, ISBN_PET_REQUEST);
        server.expect(requestTo(GOOGLE_BOOKS_REQUEST)).andRespond(withSuccess("""
                { "totalItems": 1, "items": [
                    { "volumeInfo": {
                        "title": "Clean Code",
                        "authors": ["Robert C. Martin"] } } ] }
                """, MediaType.APPLICATION_JSON));

        BarcodeLookupResponse result = service.lookup(ISBN);

        assertTrue(result.found());
        assertEquals("Clean Code - Robert C. Martin", result.name());
        assertEquals("Kitoblar", result.suggestedCategory());
        assertEquals("googlebooks", result.source());
    }

    @Test
    void cisBarcodeUsesBarcodeListBeforeOpenFactsSources() {
        server.expect(requestTo(CIS_REQUEST)).andRespond(withSuccess("""
                <!DOCTYPE html>
                <html><head>
                  <title>&#1057;&#1087;&#1083;&#1072;&#1090; &#1083;&#1077;&#1095;&#1077;&#1073;&#1085;&#1099;&#1077; &#1090;&#1088;&#1072;&#1074;&#1099; - &#1064;&#1090;&#1088;&#1080;&#1093;-&#1082;&#1086;&#1076;: 4603014022240</title>
                  <meta name="description" content="&#1064;&#1090;&#1088;&#1080;&#1093;-&#1082;&#1086;&#1076;:4603014022240 - &#1069;&#1090;&#1086;&#1090; &#1096;&#1090;&#1088;&#1080;&#1093;-&#1082;&#1086;&#1076; &#1074;&#1089;&#1090;&#1088;&#1077;&#1095;&#1072;&#1077;&#1090;&#1089;&#1103; &#1074; &#1089;&#1083;&#1077;&#1076;&#1091;&#1102;&#1097;&#1080;&#1093; &#1090;&#1086;&#1074;&#1072;&#1088;&#1072;&#1093;: &#1057;&#1087;&#1083;&#1072;&#1090; &#1083;&#1077;&#1095;&#1077;&#1073;&#1085;&#1099;&#1077; &#1090;&#1088;&#1072;&#1074;&#1099;; &#1047;&#1091;&#1073;&#1085;&#1072;&#1103; &#1087;&#1072;&#1089;&#1090;&#1072; SPLAT 100&#1075;">
                </head><body></body></html>
                """, MediaType.TEXT_HTML));

        BarcodeLookupResponse result = service.lookup(CIS_CODE);

        assertTrue(result.found());
        assertEquals("\u0421\u043f\u043b\u0430\u0442 \u043b\u0435\u0447\u0435\u0431\u043d\u044b\u0435 \u0442\u0440\u0430\u0432\u044b", result.name());
        assertEquals("Kosmetika", result.suggestedCategory());
        assertEquals("barcode-list", result.source());
        server.verify();
    }

    @Test
    void allSourcesMissReturnsNotFound() {
        expectOpenFactsMisses();
        server.expect(requestTo(UPC_REQUEST)).andRespond(withSuccess(
                "{ \"code\": \"OK\", \"total\": 0, \"items\": [] }", MediaType.APPLICATION_JSON));

        BarcodeLookupResponse result = service.lookup(CODE);

        assertFalse(result.found());
        assertNull(result.name());
        assertNull(result.suggestedCategory());
        assertNull(result.source());
    }

    @Test
    void networkErrorOnAllSourcesNeverThrowsAndReportsNotFound() {
        server.expect(requestTo(OFF_REQUEST))
                .andRespond(withException(new IOException("connect timed out")));
        server.expect(requestTo(OPF_REQUEST))
                .andRespond(withException(new IOException("connect timed out")));
        server.expect(requestTo(OBF_REQUEST))
                .andRespond(withException(new IOException("connect timed out")));
        server.expect(requestTo(PET_REQUEST))
                .andRespond(withException(new IOException("connect timed out")));
        server.expect(requestTo(UPC_REQUEST))
                .andRespond(withException(new IOException("connect timed out")));

        BarcodeLookupResponse result = service.lookup(CODE);

        assertFalse(result.found());
    }

    @Test
    void blankCodeReturnsNotFoundWithoutCallingAnyApi() {
        BarcodeLookupResponse result = service.lookup("   ");

        assertFalse(result.found());
        server.verify();
    }

    private BarcodeLookupService serviceWithKey(String key) {
        return new BarcodeLookupService(builder.build(), BARCODE_LIST_URL, OFF_URL, OPF_URL, OBF_URL, PET_URL,
                GOOGLE_BOOKS_URL, OPEN_LIBRARY_URL, UPC_URL, LOOKUP_URL, key);
    }

    private void expectOpenFactsMisses() {
        expectOpenFactsMisses(OFF_REQUEST, OPF_REQUEST, OBF_REQUEST, PET_REQUEST);
    }

    private void expectOpenFactsMisses(String off, String opf, String obf, String pet) {
        server.expect(requestTo(off)).andRespond(withSuccess("{ \"status\": 0 }",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(opf)).andRespond(withSuccess("{ \"status\": 0 }",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(obf)).andRespond(withSuccess("{ \"status\": 0 }",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(pet)).andRespond(withSuccess("{ \"status\": 0 }",
                MediaType.APPLICATION_JSON));
    }
}
