package uz.barakat.market.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Actuator health endpoint must be reachable WITHOUT authentication so
 * load balancers / uptime monitors can probe it, and must report UP — and
 * unlike the old static /api/health it factors in real DB connectivity, so a
 * dead datasource flips it to DOWN instead of falsely reporting healthy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorHealthTest {

    @Autowired
    MockMvc mvc;

    @Test
    void health_is_public_and_reports_up() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readiness_probe_is_exposed() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
