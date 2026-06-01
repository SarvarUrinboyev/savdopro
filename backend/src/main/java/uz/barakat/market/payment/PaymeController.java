package uz.barakat.market.payment;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.domain.OnlinePayment;
import uz.barakat.market.service.OnlinePaymentService;

/**
 * Payme (Paycom) Merchant API endpoint — a single JSON-RPC 2.0 method
 * router for the six required methods. Authenticates each call with the
 * HTTP Basic header {@code Paycom:{merchant_key}} and maps service results
 * (and {@link PaymeException}s) onto the JSON-RPC envelope Payme expects.
 *
 * <p>Unauthenticated at the Spring-Security layer (the provider has no JWT);
 * the merchant key in the Basic header IS the authentication. Reject codes
 * follow the Payme spec so the integration can pass sandbox certification.</p>
 */
@RestController
@RequestMapping("/api/pay/payme")
public class PaymeController {

    private static final Logger log = LoggerFactory.getLogger(PaymeController.class);
    private static final int ERR_AUTH = -32504;
    private static final int ERR_METHOD = -32601;

    private final OnlinePaymentService service;
    private final PaymentProperties properties;

    public PaymeController(OnlinePaymentService service, PaymentProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handle(@RequestBody JsonNode body,
                                      @RequestHeader(value = "Authorization", required = false)
                                      String auth) {
        Object id = body.has("id") ? body.get("id").asText() : null;
        if (!properties.payme().isUsable() || !authorized(auth)) {
            return error(id, ERR_AUTH, "Ruxsat yo'q");
        }
        String method = body.path("method").asText("");
        JsonNode p = body.path("params");
        try {
            return switch (method) {
                case "CheckPerformTransaction" -> checkPerform(id, p);
                case "CreateTransaction" -> create(id, p);
                case "PerformTransaction" -> perform(id, p);
                case "CancelTransaction" -> cancel(id, p);
                case "CheckTransaction" -> check(id, p);
                case "GetStatement" -> statement(id);
                default -> error(id, ERR_METHOD, "Metod topilmadi");
            };
        } catch (PaymeException ex) {
            return error(id, ex.getCode(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Payme {} failed: {}", method, ex.toString());
            return error(id, ERR_METHOD, "Ichki xatolik");
        }
    }

    // ------------------------------------------------------------- methods

    private Map<String, Object> checkPerform(Object id, JsonNode p) {
        long amount = p.path("amount").asLong();
        service.paymeCheckPerform(account(p), amount);
        return result(id, Map.of("allow", true));
    }

    private Map<String, Object> create(Object id, JsonNode p) {
        String txnId = p.path("id").asText();
        long amount = p.path("amount").asLong();
        long time = p.path("time").asLong();
        OnlinePayment op = service.paymeCreate(txnId, account(p), amount, time);
        Map<String, Object> r = new HashMap<>();
        r.put("create_time", op.getCreateTimeMs() == null ? time : op.getCreateTimeMs());
        r.put("transaction", String.valueOf(op.getId()));
        r.put("state", wireState(op));
        return result(id, r);
    }

    private Map<String, Object> perform(Object id, JsonNode p) {
        OnlinePayment op = service.paymePerform(p.path("id").asText());
        Map<String, Object> r = new HashMap<>();
        r.put("transaction", String.valueOf(op.getId()));
        r.put("perform_time", op.getPerformTimeMs() == null ? 0L : op.getPerformTimeMs());
        r.put("state", wireState(op));
        return result(id, r);
    }

    private Map<String, Object> cancel(Object id, JsonNode p) {
        int reason = p.path("reason").asInt();
        OnlinePayment op = service.paymeCancel(p.path("id").asText(), reason);
        Map<String, Object> r = new HashMap<>();
        r.put("transaction", String.valueOf(op.getId()));
        r.put("cancel_time", op.getCancelTimeMs() == null ? 0L : op.getCancelTimeMs());
        r.put("state", wireState(op));
        return result(id, r);
    }

    private Map<String, Object> check(Object id, JsonNode p) {
        OnlinePayment op = service.paymeFind(p.path("id").asText());
        Map<String, Object> r = new HashMap<>();
        r.put("create_time", op.getCreateTimeMs() == null ? 0L : op.getCreateTimeMs());
        r.put("perform_time", op.getPerformTimeMs() == null ? 0L : op.getPerformTimeMs());
        r.put("cancel_time", op.getCancelTimeMs() == null ? 0L : op.getCancelTimeMs());
        r.put("transaction", String.valueOf(op.getId()));
        r.put("state", wireState(op));
        r.put("reason", op.getReason());
        return result(id, r);
    }

    private Map<String, Object> statement(Object id) {
        return result(id, Map.of("transactions", List.of()));
    }

    // ------------------------------------------------------------- helpers

    /** Internal state -> Payme wire state (created is 1 on the wire, 0 internally). */
    private static int wireState(OnlinePayment op) {
        return op.getState() == OnlinePayment.STATE_CREATED ? 1 : op.getState();
    }

    private static String account(JsonNode p) {
        JsonNode acc = p.path("account");
        // Take the first (and only) account field value, whatever its key.
        if (acc.isObject() && acc.fieldNames().hasNext()) {
            return acc.get(acc.fieldNames().next()).asText();
        }
        return null;
    }

    private boolean authorized(String auth) {
        if (auth == null || !auth.startsWith("Basic ")) {
            return false;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(auth.substring(6).trim()),
                    StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            String pass = colon >= 0 ? decoded.substring(colon + 1) : "";
            return pass.equals(properties.payme().key());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static Map<String, Object> result(Object id, Object result) {
        Map<String, Object> m = new HashMap<>();
        m.put("result", result);
        m.put("id", id);
        return m;
    }

    private static Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> err = new HashMap<>();
        err.put("code", code);
        err.put("message", message);
        Map<String, Object> m = new HashMap<>();
        m.put("error", err);
        m.put("id", id);
        return m;
    }
}
