package uz.barakat.market.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With no scrape token configured (the default), /actuator/prometheus must
 * stay JWT-only — an arbitrary bearer token gets a 401, proving the filter
 * is inert until explicitly enabled.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MetricsScrapeTokenDisabledTest {

    @Autowired
    MockMvc mvc;

    @Test
    void prometheus_stays_locked_when_token_unset() throws Exception {
        mvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer any-random-value"))
                .andExpect(status().isUnauthorized());
    }
}
