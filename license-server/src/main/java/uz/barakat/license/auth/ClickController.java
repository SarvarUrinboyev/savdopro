package uz.barakat.license.auth;

import java.util.Map;
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
 */
@RestController
@RequestMapping("/api/billing/click")
public class ClickController {

    private final ClickPaymentService click;

    public ClickController(ClickPaymentService click) {
        this.click = click;
    }

    @PostMapping("/prepare")
    public Map<String, Object> prepare(
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
        return click.prepare(new ClickCallback(clickTransId, serviceId, clickPaydocId,
                merchantTransId, null, amount, action, error, errorNote, signTime, signString));
    }

    @PostMapping("/complete")
    public Map<String, Object> complete(
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
        return click.complete(new ClickCallback(clickTransId, serviceId, clickPaydocId,
                merchantTransId, merchantPrepareId, amount, action, error, errorNote,
                signTime, signString));
    }
}
