package uz.barakat.license.auth;

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
 * the scraper — and nothing else. Unauthorised callers on this server get 403
 * (it has no custom auth entry point — established behaviour), and the scrape
 * token must never work on the admin/auth API.
 *
 * <p>{@code @AutoConfigureObservability} matters: plain {@code @SpringBootTest}
 * disables metrics exporters, so without it the prometheus endpoint is absent
 * and this test would 404.
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
                // Real Prometheus exposition text, not an error page.
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }

    @Test
    void wrong_token_is_rejected() throws Exception {
        mvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void scrape_token_does_not_open_admin_endpoints() throws Exception {
        mvc.perform(get("/api/admin/accounts")
                        .header("Authorization", "Bearer test-scrape-token-123"))
                .andExpect(status().isForbidden());
    }
}
