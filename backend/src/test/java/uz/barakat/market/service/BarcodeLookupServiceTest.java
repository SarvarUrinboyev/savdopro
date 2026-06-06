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
 * Unit tests for the global barcode lookup: source fall-through (Open Food Facts
 * then UPC Item DB) and the never-throw contract. The HTTP calls are mocked with
 * {@link MockRestServiceServer} bound to the service's {@link RestClient}, so no
 * real network is touched.
 */
class BarcodeLookupServiceTest {

    private static final String OFF_URL = "https://off.test/api/v2/product/{barcode}.json";
    private static final String UPC_URL = "https://upc.test/prod/trial/lookup?upc={barcode}";
    private static final String CODE = "4870204010023";
    private static final String OFF_REQUEST = "https://off.test/api/v2/product/" + CODE + ".json";
    private static final String UPC_REQUEST = "https://upc.test/prod/trial/lookup?upc=" + CODE;

    private MockRestServiceServer server;
    private BarcodeLookupService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new BarcodeLookupService(builder.build(), OFF_URL, UPC_URL);
    }

    @Test
    void openFoodFactsHitReturnsNameAndCategoryWithoutCallingUpc() {
        server.expect(requestTo(OFF_REQUEST)).andRespond(withSuccess("""
                { "status": 1, "product": {
                    "product_name": "Coca-Cola 0.5L",
                    "categories_tags": ["en:beverages", "en:sodas"] } }
                """, MediaType.APPLICATION_JSON));

        BarcodeLookupResponse result = service.lookup(CODE);

        assertTrue(result.found());
        assertEquals("Coca-Cola 0.5L", result.name());
        assertEquals("Beverages", result.suggestedCategory());
        assertEquals("openfoodfacts", result.source());
        server.verify();   // only OFF was declared — UPC must NOT have been called
    }

    @Test
    void prefersUzbekProductNameWhenPresent() {
        server.expect(requestTo(OFF_REQUEST)).andRespond(withSuccess("""
                { "status": 1, "product": {
                    "product_name": "Cola",
                    "product_name_uz": "Kola 0.5L",
                    "categories_tags": [] } }
                """, MediaType.APPLICATION_JSON));

        BarcodeLookupResponse result = service.lookup(CODE);

        assertEquals("Kola 0.5L", result.name());
        assertNull(result.suggestedCategory());   // empty tags → no category
    }

    @Test
    void fallsThroughToUpcItemDbWhenOpenFoodFactsMisses() {
        server.expect(requestTo(OFF_REQUEST)).andRespond(withSuccess(
                "{ \"status\": 0, \"status_verbose\": \"product not found\" }",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(UPC_REQUEST)).andRespond(withSuccess("""
                { "code": "OK", "total": 1, "items": [
                    { "title": "Snickers Bar 50g",
                      "category": "Food, Beverages & Tobacco > Food Items > Candy" } ] }
                """, MediaType.APPLICATION_JSON));

        BarcodeLookupResponse result = service.lookup(CODE);

        assertTrue(result.found());
        assertEquals("Snickers Bar 50g", result.name());
        assertEquals("Candy", result.suggestedCategory());   // last, most specific segment
        assertEquals("upcitemdb", result.source());
    }

    @Test
    void bothSourcesMissReturnsNotFound() {
        server.expect(requestTo(OFF_REQUEST))
                .andRespond(withSuccess("{ \"status\": 0 }", MediaType.APPLICATION_JSON));
        server.expect(requestTo(UPC_REQUEST)).andRespond(withSuccess(
                "{ \"code\": \"OK\", \"total\": 0, \"items\": [] }", MediaType.APPLICATION_JSON));

        BarcodeLookupResponse result = service.lookup(CODE);

        assertFalse(result.found());
        assertNull(result.name());
        assertNull(result.suggestedCategory());
        assertNull(result.source());
    }

    @Test
    void networkErrorOnBothSourcesNeverThrowsAndReportsNotFound() {
        server.expect(requestTo(OFF_REQUEST))
                .andRespond(withException(new IOException("connect timed out")));
        server.expect(requestTo(UPC_REQUEST))
                .andRespond(withException(new IOException("connect timed out")));

        BarcodeLookupResponse result = service.lookup(CODE);

        assertFalse(result.found());   // a timeout degrades to a miss, it does not throw
    }

    @Test
    void blankCodeReturnsNotFoundWithoutCallingAnyApi() {
        // No server.expect(...) is declared, so any HTTP call would fail verify().
        BarcodeLookupResponse result = service.lookup("   ");

        assertFalse(result.found());
        server.verify();
    }
}
