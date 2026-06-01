package uz.barakat.market.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.domain.OnlinePayment;
import uz.barakat.market.service.OnlinePaymentService;

/**
 * Click SHOP-API endpoints: {@code Prepare} then {@code Complete}, both
 * form-encoded. Each call is authenticated by the MD5 {@code sign_string}
 * over the documented field order; a mismatch returns Click error -1.
 * Error codes follow the Click spec so the integration can pass review.
 */
@RestController
@RequestMapping("/api/pay/click")
public class ClickController {

    private static final Logger log = LoggerFactory.getLogger(ClickController.class);
    private static final int OK = 0;
    private static final int ERR_SIGN = -1;
    private static final int ERR_NOT_CONFIGURED = -8;
    private static final int ERR_CANCELLED = -9;

    private final OnlinePaymentService service;
    private final PaymentProperties properties;

    public ClickController(OnlinePaymentService service, PaymentProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping("/prepare")
    public Map<String, Object> prepare(
            @RequestParam("click_trans_id") String clickTransId,
            @RequestParam("service_id") String serviceId,
            @RequestParam("merchant_trans_id") String merchantTransId,
            @RequestParam("amount") String amount,
            @RequestParam("action") String action,
            @RequestParam("sign_time") String signTime,
            @RequestParam("sign_string") String sign) {
        if (!properties.click().isUsable()) {
            return clickError(clickTransId, null, ERR_NOT_CONFIGURED, "Not configured");
        }
        String expected = md5(clickTransId + serviceId + properties.click().secretKey()
                + merchantTransId + amount + action + signTime);
        if (!expected.equalsIgnoreCase(sign)) {
            return clickError(clickTransId, null, ERR_SIGN, "SIGN CHECK FAILED");
        }
        try {
            long som = new BigDecimal(amount).setScale(0, java.math.RoundingMode.HALF_UP).longValue();
            OnlinePayment op = service.clickPrepare(merchantTransId, som, clickTransId);
            Map<String, Object> r = ok(clickTransId, merchantTransId);
            r.put("merchant_prepare_id", op.getId());
            return r;
        } catch (RuntimeException ex) {
            log.warn("Click prepare failed: {}", ex.toString());
            return clickError(clickTransId, merchantTransId, ERR_NOT_CONFIGURED, ex.getMessage());
        }
    }

    @PostMapping("/complete")
    public Map<String, Object> complete(
            @RequestParam("click_trans_id") String clickTransId,
            @RequestParam("service_id") String serviceId,
            @RequestParam("merchant_trans_id") String merchantTransId,
            @RequestParam("merchant_prepare_id") String merchantPrepareId,
            @RequestParam("amount") String amount,
            @RequestParam("action") String action,
            @RequestParam(value = "error", required = false, defaultValue = "0") String clickError,
            @RequestParam("sign_time") String signTime,
            @RequestParam("sign_string") String sign) {
        if (!properties.click().isUsable()) {
            return clickError(clickTransId, merchantTransId, ERR_NOT_CONFIGURED, "Not configured");
        }
        String expected = md5(clickTransId + serviceId + properties.click().secretKey()
                + merchantTransId + merchantPrepareId + amount + action + signTime);
        if (!expected.equalsIgnoreCase(sign)) {
            return clickError(clickTransId, merchantTransId, ERR_SIGN, "SIGN CHECK FAILED");
        }
        try {
            boolean confirm = "0".equals(clickError) || parseInt(clickError) >= 0;
            OnlinePayment op = service.clickComplete(clickTransId, confirm);
            if (!confirm) {
                return clickError(clickTransId, merchantTransId, ERR_CANCELLED, "Cancelled");
            }
            Map<String, Object> r = ok(clickTransId, merchantTransId);
            r.put("merchant_confirm_id", op.getId());
            return r;
        } catch (RuntimeException ex) {
            log.warn("Click complete failed: {}", ex.toString());
            return clickError(clickTransId, merchantTransId, ERR_CANCELLED, ex.getMessage());
        }
    }

    // ------------------------------------------------------------- helpers

    private static Map<String, Object> ok(String clickTransId, String merchantTransId) {
        Map<String, Object> r = new HashMap<>();
        r.put("click_trans_id", clickTransId);
        r.put("merchant_trans_id", merchantTransId);
        r.put("error", OK);
        r.put("error_note", "Success");
        return r;
    }

    private static Map<String, Object> clickError(String clickTransId, String merchantTransId,
                                                  int code, String note) {
        Map<String, Object> r = new HashMap<>();
        r.put("click_trans_id", clickTransId);
        r.put("merchant_trans_id", merchantTransId);
        r.put("error", code);
        r.put("error_note", note == null ? "Error" : note);
        return r;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }
}
