package uz.barakat.license.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared-service routing: a NUMERIC merchant_trans_id (SavdoPRO's own Payment
 * id) is handled locally by ClickPaymentService; a hex id (the co-tenant,
 * TezGo) is relayed verbatim to the forwarder and never touches the local
 * billing path. This is what lets one Click service serve both apps.
 *
 * <p>Full context (the Click endpoints are permitAll in SecurityConfig);
 * ClickPaymentService + the forwarder are mocked so we assert only routing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClickControllerRoutingTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ClickPaymentService click;

    @MockBean
    ClickGatewayForwarder forwarder;

    @Test
    void numericMerchantTransId_handledLocally() throws Exception {
        when(click.prepare(any())).thenReturn(Map.of(
                "click_trans_id", "1", "merchant_trans_id", "6",
                "error", 0, "error_note", "Success", "merchant_prepare_id", 6));

        mvc.perform(post("/api/billing/click/prepare")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("click_trans_id", "1")
                        .param("service_id", "105926")
                        .param("merchant_trans_id", "6")
                        .param("amount", "99000")
                        .param("action", "0")
                        .param("sign_time", "2026-07-02 09:00:00")
                        .param("sign_string", "abc"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"error\":0")));

        verify(click).prepare(any());
        verify(forwarder, never()).forward(anyString(), any());
    }

    @Test
    void hexMerchantTransId_relayedToCoTenant() throws Exception {
        when(forwarder.forward(eq("prepare"), any())).thenReturn(
                "{\"click_trans_id\":\"1\",\"merchant_trans_id\":\"0376fe49e2ac4aca9a1d\",\"error\":0}");

        mvc.perform(post("/api/billing/click/prepare")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("click_trans_id", "1")
                        .param("service_id", "105926")
                        .param("merchant_trans_id", "0376fe49e2ac4aca9a1d")
                        .param("amount", "100000")
                        .param("action", "0")
                        .param("sign_time", "2026-07-02 09:00:00")
                        .param("sign_string", "abc"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("0376fe49e2ac4aca9a1d")));

        verify(forwarder).forward(eq("prepare"), any());
        verify(click, never()).prepare(any());
    }

    @Test
    void relayFailure_returns502SoClickRetries() throws Exception {
        when(forwarder.forward(eq("complete"), any())).thenReturn(null); // co-tenant down

        mvc.perform(post("/api/billing/click/complete")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("click_trans_id", "1")
                        .param("merchant_trans_id", "6ee95aca58f24ff0bc64")
                        .param("merchant_prepare_id", "1")
                        .param("amount", "100000")
                        .param("action", "1")
                        .param("sign_time", "2026-07-02 09:00:00")
                        .param("sign_string", "abc"))
                .andExpect(status().isBadGateway());

        verify(click, never()).complete(any());
    }
}
