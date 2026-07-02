package uz.barakat.license.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Click SHOP-API callbacks. PUBLIC (Click can't carry a user JWT) and
 * {@code application/x-www-form-urlencoded}; the security boundary is the
 * MD5 {@code sign_string} Click computes with the shared secret, verified in
 * {@link ClickPaymentService}. Each method echoes Click's exact JSON shape
 * with a numeric {@code error} code.
 *
 * <h2>Shared-service routing</h2>
 * One Click service (merchant + service_id + secret) is shared with a second
 * project (TezGo). We tell the two apart by {@code merchant_trans_id} shape:
 * SavdoPRO subscription payments carry the NUMERIC Payment id; the co-tenant
 * uses a 20-char hex id. Numeric → handled locally; anything else →
 * forwarded verbatim to the co-tenant via {@link ClickGatewayForwarder}, its
 * reply returned to Click unchanged. This lets both apps live under one Click
 * service without a second registration and without touching the other box.
 */
@RestController
@RequestMapping("/api/billing/click")
public class ClickController {

    private static final Logger log = LoggerFactory.getLogger(ClickController.class);

    private final ClickPaymentService click;
    private final ClickGatewayForwarder forwarder;
    private final ObjectMapper json;

    public ClickController(ClickPaymentService click, ClickGatewayForwarder forwarder,
                           ObjectMapper json) {
        this.click = click;
        this.forwarder = forwarder;
        this.json = json;
    }

    /** SavdoPRO's own merchant_trans_id is the numeric Payment id. */
    private static boolean isLocal(String merchantTransId) {
        return merchantTransId != null && merchantTransId.matches("\\d+");
    }

    @PostMapping("/prepare")
    public ResponseEntity<String> prepare(
            @RequestParam(name = "click_trans_id", required = false) String clickTransId,
            @RequestParam(name = "service_id", required = false) String serviceId,
            @RequestParam(name = "click_paydoc_id", required = false) String clickPaydocId,
            @RequestParam(name = "merchant_trans_id", required = false) String merchantTransId,
            @RequestParam(name = "amount", required = false) String amount,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "error_note", required = false) String errorNote,
            @RequestParam(name = "sign_time", required = false) String signTime,
            @RequestParam(name = "sign_string", required = false) String signString) {
        if (isLocal(merchantTransId)) {
            return jsonReply(click.prepare(new ClickCallback(clickTransId, serviceId, clickPaydocId,
                    merchantTransId, null, amount, action, error, errorNote, signTime, signString)));
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("click_trans_id", clickTransId);
        params.put("service_id", serviceId);
        params.put("click_paydoc_id", clickPaydocId);
        params.put("merchant_trans_id", merchantTransId);
        params.put("amount", amount);
        params.put("action", action);
        params.put("error", error);
        params.put("error_note", errorNote);
        params.put("sign_time", signTime);
        params.put("sign_string", signString);
        return relay("prepare", params);
    }

    @PostMapping("/complete")
    public ResponseEntity<String> complete(
            @RequestParam(name = "click_trans_id", required = false) String clickTransId,
            @RequestParam(name = "service_id", required = false) String serviceId,
            @RequestParam(name = "click_paydoc_id", required = false) String clickPaydocId,
            @RequestParam(name = "merchant_trans_id", required = false) String merchantTransId,
            @RequestParam(name = "merchant_prepare_id", required = false) String merchantPrepareId,
            @RequestParam(name = "amount", required = false) String amount,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "error_note", required = false) String errorNote,
            @RequestParam(name = "sign_time", required = false) String signTime,
            @RequestParam(name = "sign_string", required = false) String signString) {
        if (isLocal(merchantTransId)) {
            return jsonReply(click.complete(new ClickCallback(clickTransId, serviceId, clickPaydocId,
                    merchantTransId, merchantPrepareId, amount, action, error, errorNote,
                    signTime, signString)));
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("click_trans_id", clickTransId);
        params.put("service_id", serviceId);
        params.put("click_paydoc_id", clickPaydocId);
        params.put("merchant_trans_id", merchantTransId);
        params.put("merchant_prepare_id", merchantPrepareId);
        params.put("amount", amount);
        params.put("action", action);
        params.put("error", error);
        params.put("error_note", errorNote);
        params.put("sign_time", signTime);
        params.put("sign_string", signString);
        return relay("complete", params);
    }

    private ResponseEntity<String> relay(String leg, Map<String, String> params) {
        String body = forwarder.forward(leg, params);
        if (body == null) {
            // Co-tenant unreachable → 502 so Click RETRIES rather than treats
            // it as a terminal failure; the taxi payment is re-delivered.
            log.warn("Click {} relay unavailable for merchant_trans_id={}",
                    leg, params.get("merchant_trans_id"));
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":-8,\"error_note\":\"Gateway unavailable\"}");
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private ResponseEntity<String> jsonReply(Map<String, Object> reply) {
        try {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(reply));
        } catch (JsonProcessingException e) {
            // Should never happen for a plain string/number map.
            throw new IllegalStateException("Click reply serialization failed", e);
        }
    }
}
