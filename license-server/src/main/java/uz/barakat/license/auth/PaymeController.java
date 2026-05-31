package uz.barakat.license.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payme (Paycom) Merchant-API endpoint: a single JSON-RPC 2.0 method router.
 * PUBLIC (Payme carries no user JWT) — authenticated by HTTP Basic over the
 * shared merchant key (login {@code Paycom}, password = the key), checked
 * constant-time here. Each method delegates to {@link PaymeService}; results
 * and {@link PaymeException}s are wrapped in the JSON-RPC envelope Payme
 * expects, with the request {@code id} echoed back.
 */
@RestController
@RequestMapping("/api/billing/payme")
public class PaymeController {

    private static final Logger log = LoggerFactory.getLogger(PaymeController.class);

    private static final int ERR_AUTH = -32504;
    private static final int ERR_METHOD = -32601;
    private static final int ERR_INTERNAL = -32400;

    private final PaymeService payme;
    private final String merchantKey;

    public PaymeController(PaymeService payme,
                           @Value("${billing.payme.merchant-key:}") String merchantKey) {
        this.payme = payme;
        this.merchantKey = merchantKey;
    }

    @PostMapping
    public Map<String, Object> handle(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        Object id = body.get("id");
        if (!authorized(auth)) {
            return error(id, ERR_AUTH, "Ruxsat yo'q");
        }
        String method = String.valueOf(body.get("method"));
        Map<String, Object> params = asMap(body.get("params"));
        try {
            Map<String, Object> result = switch (method) {
                case "CheckPerformTransaction" -> payme.checkPerformTransaction(params);
                case "CreateTransaction" -> payme.createTransaction(params);
                case "PerformTransaction" -> payme.performTransaction(params);
                case "CancelTransaction" -> payme.cancelTransaction(params);
                case "CheckTransaction" -> payme.checkTransaction(params);
                default -> throw new PaymeException(ERR_METHOD, "Metod topilmadi: " + method);
            };
            return ok(id, result);
        } catch (PaymeException e) {
            return error(id, e.code(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Payme handler error on method={}: {}", method, e.toString());
            return error(id, ERR_INTERNAL, "Server xatosi");
        }
    }

    private boolean authorized(String auth) {
        // Not configured → reject everything rather than accept unsigned calls.
        if (merchantKey == null || merchantKey.isBlank()) {
            return false;
        }
        if (auth == null || !auth.startsWith("Basic ")) {
            return false;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(auth.substring(6).trim()),
                    StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            String password = colon >= 0 ? decoded.substring(colon + 1) : decoded;
            return MessageDigest.isEqual(
                    password.getBytes(StandardCharsets.UTF_8),
                    merchantKey.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return false; // malformed base64
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (o instanceof Map<?, ?> m) ? (Map<String, Object>) m : new LinkedHashMap<>();
    }

    private static Map<String, Object> ok(Object id, Map<String, Object> result) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("jsonrpc", "2.0");
        r.put("id", id);
        r.put("result", result);
        return r;
    }

    private static Map<String, Object> error(Object id, int code, String message) {
        // Payme wants a localized message object; mirror the text across locales.
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("uz", message);
        msg.put("ru", message);
        msg.put("en", message);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", msg);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("jsonrpc", "2.0");
        r.put("id", id);
        r.put("error", err);
        return r;
    }
}
