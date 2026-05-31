package uz.barakat.license.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The JSON-RPC envelope + Basic-auth gate around {@link PaymeService}: a good
 * key + known method is wrapped as {result}, a bad key is -32504 (without ever
 * reaching the service), a service {@link PaymeException} becomes the matching
 * error code, and an unknown method is -32601.
 */
@ExtendWith(MockitoExtension.class)
class PaymeControllerTest {

    private static final String KEY = "merchant-key";

    @Mock private PaymeService payme;
    private PaymeController controller;

    @BeforeEach
    void setUp() {
        controller = new PaymeController(payme, KEY);
    }

    @Test
    void wrapsServiceResultInJsonRpcEnvelope() {
        when(payme.checkPerformTransaction(any())).thenReturn(Map.of("allow", true));

        Map<String, Object> r = controller.handle(
                Map.of("jsonrpc", "2.0", "id", 7, "method", "CheckPerformTransaction",
                        "params", Map.of()),
                basic("Paycom", KEY));

        assertThat(r.get("id")).isEqualTo(7);
        assertThat(r).doesNotContainKey("error");
        assertThat(((Map<?, ?>) r.get("result")).get("allow")).isEqualTo(true);
    }

    @Test
    void rejectsBadCredentialsWithoutTouchingTheService() {
        Map<String, Object> r = controller.handle(
                Map.of("id", 1, "method", "CheckPerformTransaction", "params", Map.of()),
                basic("Paycom", "wrong-key"));

        assertThat(((Map<?, ?>) r.get("error")).get("code")).isEqualTo(-32504);
        verifyNoInteractions(payme);
    }

    @Test
    void rejectsMissingAuthorizationHeader() {
        Map<String, Object> r = controller.handle(
                Map.of("id", 1, "method", "CheckPerformTransaction", "params", Map.of()), null);

        assertThat(((Map<?, ?>) r.get("error")).get("code")).isEqualTo(-32504);
        verifyNoInteractions(payme);
    }

    @Test
    void mapsPaymeExceptionToMatchingErrorCode() {
        when(payme.performTransaction(any()))
                .thenThrow(new PaymeException(-31003, "Tranzaksiya topilmadi"));

        Map<String, Object> r = controller.handle(
                Map.of("id", 2, "method", "PerformTransaction", "params", Map.of("id", "T1")),
                basic("Paycom", KEY));

        assertThat(((Map<?, ?>) r.get("error")).get("code")).isEqualTo(-31003);
    }

    @Test
    void unknownMethodReturnsMethodNotFound() {
        Map<String, Object> r = controller.handle(
                Map.of("id", 3, "method", "DoesNotExist", "params", Map.of()),
                basic("Paycom", KEY));

        assertThat(((Map<?, ?>) r.get("error")).get("code")).isEqualTo(-32601);
    }

    private static String basic(String login, String pass) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((login + ":" + pass).getBytes(StandardCharsets.UTF_8));
    }
}
