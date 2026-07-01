package uz.barakat.market.auth;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A configured {@code METRICS_SCRAPE_TOKEN} must open /actuator/prometheus to
 * the scraper — and nothing else. Wrong or missing tokens keep the endpoint
 * locked, and the scrape token must never work on business endpoints.
 *
 * <p>{@code @AutoConfigureObservability} matters: plain {@code @SpringBootTest}
 * disables metrics exporters, so without it the prometheus endpoint is absent
 * and a passing 200 would actually be the SPA index.html fallback. The body
 * assertion below guards against that false positive.
 */
@SpringBootTest(properties = "savdopro.metrics.scrape-token=test-scrape-token-123")
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
class MetricsScrapeTokenFilterTest {

    @Autowired
    MockMvc mvc;

    @Test
    void correct_token_can_scrape_prometheus() throws Exception {
        mvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer test-scrape-token-123"))
                .andExpect(status().isOk())
                // Real Prometheus exposition text, not the SPA shell.
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }

    @Test
    void wrong_token_is_rejected() throws Exception {
        mvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missing_token_is_rejected() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scrape_token_does_not_open_business_endpoints() throws Exception {
        mvc.perform(get("/api/products")
                        .header("Authorization", "Bearer test-scrape-token-123"))
                .andExpect(status().isUnauthorized());
    }
}
