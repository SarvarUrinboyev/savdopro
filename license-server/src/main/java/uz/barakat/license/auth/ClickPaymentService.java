package uz.barakat.license.auth;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uz.barakat.license.domain.Payment;
import uz.barakat.license.domain.PaymentStatus;
import uz.barakat.license.repository.PaymentRepository;

/**
 * Click SHOP-API protocol: the two-stage Prepare (action=0) / Complete
 * (action=1) handshake. Each leg is authenticated by Click's MD5
 * {@code sign_string} (see {@link ClickSignature}); we then validate the
 * order against our {@link Payment} row and, on a successful Complete, flip
 * it to PAID via {@link BillingService#confirmPayment} — which extends the
 * subscription. Responses use Click's exact JSON field names and numeric
 * error codes so Click accepts them verbatim.
 */
@Service
public class ClickPaymentService {

    private static final Logger log = LoggerFactory.getLogger(ClickPaymentService.class);

    // Click result codes (a subset; negative = reject).
    static final int OK = 0;
    static final int SIGN_FAILED = -1;
    static final int BAD_AMOUNT = -2;
    static final int ALREADY_PAID = -4;
    static final int ORDER_NOT_FOUND = -5;
    static final int TX_NOT_FOUND = -6;

    private final PaymentRepository payments;
    private final BillingService billing;
    private final String secretKey;

    public ClickPaymentService(PaymentRepository payments, BillingService billing,
                               @Value("${billing.click.secret-key:}") String secretKey) {
        this.payments = payments;
        this.billing = billing;
        this.secretKey = secretKey;
    }

    /** Prepare leg: validate the order exists, is unpaid, and the amount matches. */
    public Map<String, Object> prepare(ClickCallback cb) {
        if (!ClickSignature.matches(ClickSignature.expectedPrepare(cb, secretKey), cb.signString())) {
            return reply(cb, SIGN_FAILED, "Imzo noto'g'ri");
        }
        Optional<Payment> found = findPayment(cb.merchantTransId());
        if (found.isEmpty()) {
            return reply(cb, ORDER_NOT_FOUND, "Buyurtma topilmadi");
        }
        Payment p = found.get();
        if (p.getStatus() == PaymentStatus.PAID) {
            return reply(cb, ALREADY_PAID, "Allaqachon to'langan");
        }
        if (!amountMatches(p, cb.amount())) {
            return reply(cb, BAD_AMOUNT, "Summa noto'g'ri");
        }
        Map<String, Object> ok = reply(cb, OK, "Success");
        ok.put("merchant_prepare_id", p.getId());
        return ok;
    }

    /** Complete leg: on success confirm the payment and extend the subscription. */
    public Map<String, Object> complete(ClickCallback cb) {
        if (!ClickSignature.matches(ClickSignature.expectedComplete(cb, secretKey), cb.signString())) {
            return reply(cb, SIGN_FAILED, "Imzo noto'g'ri");
        }
        Optional<Payment> found = findPayment(cb.merchantTransId());
        if (found.isEmpty()) {
            return reply(cb, TX_NOT_FOUND, "Tranzaksiya topilmadi");
        }
        Payment p = found.get();
        // Click reports its own negative error (e.g. the customer cancelled on
        // their side). Mark the order failed and echo the code back to Click.
        int clickError = parseInt(cb.error());
        if (clickError < 0) {
            billing.markFailed(p.getId());
            return reply(cb, clickError, "Click bekor qildi");
        }
        if (p.getStatus() == PaymentStatus.PAID) { // idempotent re-delivery
            return confirmed(cb, p);
        }
        if (!amountMatches(p, cb.amount())) {
            return reply(cb, BAD_AMOUNT, "Summa noto'g'ri");
        }
        billing.confirmPayment(p.getId(), cb.clickTransId());
        log.info("Click payment {} confirmed (click_trans_id={})", p.getId(), cb.clickTransId());
        return confirmed(cb, p);
    }

    private static Map<String, Object> confirmed(ClickCallback cb, Payment p) {
        Map<String, Object> ok = reply(cb, OK, "Success");
        ok.put("merchant_confirm_id", p.getId());
        return ok;
    }

    private Optional<Payment> findPayment(String merchantTransId) {
        Long id = parseLongOrNull(merchantTransId);
        return id == null ? Optional.empty() : payments.findById(id);
    }

    private static boolean amountMatches(Payment p, String clickAmount) {
        if (clickAmount == null) {
            return false;
        }
        try {
            return new BigDecimal(clickAmount)
                    .compareTo(BigDecimal.valueOf(p.getAmountUzs())) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Map<String, Object> reply(ClickCallback cb, int code, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("click_trans_id", cb.clickTransId());
        m.put("merchant_trans_id", cb.merchantTransId());
        m.put("error", code);
        m.put("error_note", note);
        return m;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static Long parseLongOrNull(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
