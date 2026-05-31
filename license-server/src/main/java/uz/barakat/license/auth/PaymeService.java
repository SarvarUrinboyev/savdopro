package uz.barakat.license.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.license.domain.Payment;
import uz.barakat.license.domain.PaymentStatus;
import uz.barakat.license.repository.PaymentRepository;

/**
 * Payme (Paycom) Merchant-API state machine. Payme tracks a transaction
 * separately from our order, walking it Created (1) → Performed (2) or
 * Cancelled (-1 before / -2 after perform). We mirror that on the
 * {@link Payment} row so every method is idempotent — Payme may retry any
 * call. A successful {@code PerformTransaction} is what extends the
 * subscription (via {@link BillingService#confirmPayment}); a cancel marks
 * the payment FAILED. Amounts are in tiyin (so'm × 100).
 *
 * <p>Authentication (Basic auth over the shared merchant key) is handled in
 * {@link PaymeController}; this class assumes the caller is Payme.
 */
@Service
public class PaymeService {

    private static final Logger log = LoggerFactory.getLogger(PaymeService.class);

    // Transaction states (Payme convention).
    static final int STATE_CREATED = 1;
    static final int STATE_PERFORMED = 2;
    static final int STATE_CANCELLED = -1;
    static final int STATE_CANCELLED_AFTER = -2;

    // JSON-RPC / Payme error codes.
    static final int ERR_INVALID_AMOUNT = -31001;
    static final int ERR_TX_NOT_FOUND = -31003;
    static final int ERR_CANT_PERFORM = -31008;
    static final int ERR_ORDER_NOT_FOUND = -31050; // merchant-defined range
    static final int ERR_ORDER_STATE = -31051;

    private final PaymentRepository payments;
    private final BillingService billing;
    private final String accountField;

    public PaymeService(PaymentRepository payments, BillingService billing,
                        @Value("${billing.payme.account-field:order_id}") String accountField) {
        this.payments = payments;
        this.billing = billing;
        this.accountField = accountField;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> checkPerformTransaction(Map<String, Object> params) {
        Payment p = orderFromAccount(params);
        requirePayable(p, amount(params));
        return one("allow", true);
    }

    @Transactional
    public Map<String, Object> createTransaction(Map<String, Object> params) {
        String txId = str(params.get("id"));
        long time = longVal(params.get("time"), System.currentTimeMillis());
        Payment p = orderFromAccount(params);

        if (p.getPaymeTxId() != null) {
            if (p.getPaymeTxId().equals(txId)) {
                return createResult(p); // idempotent re-create
            }
            // A different Payme transaction already owns this order.
            throw new PaymeException(ERR_ORDER_STATE, "Buyurtma boshqa tranzaksiyada band");
        }
        requirePayable(p, amount(params));
        p.setPaymeTxId(txId);
        p.setPaymeState(STATE_CREATED);
        p.setPaymeCreateTime(time);
        payments.save(p);
        return createResult(p);
    }

    @Transactional
    public Map<String, Object> performTransaction(Map<String, Object> params) {
        Payment p = byTx(str(params.get("id")));
        Integer state = p.getPaymeState();
        if (state != null && state == STATE_PERFORMED) {
            return performResult(p); // idempotent
        }
        if (state == null || state != STATE_CREATED) {
            throw new PaymeException(ERR_CANT_PERFORM, "Tranzaksiyani bajarib bo'lmaydi");
        }
        p.setPaymeState(STATE_PERFORMED);
        p.setPaymePerformTime(System.currentTimeMillis());
        payments.save(p);
        billing.confirmPayment(p.getId(), "payme:" + p.getPaymeTxId());
        log.info("Payme tx {} performed for payment {}", p.getPaymeTxId(), p.getId());
        return performResult(p);
    }

    @Transactional
    public Map<String, Object> cancelTransaction(Map<String, Object> params) {
        Payment p = byTx(str(params.get("id")));
        int reason = (int) longVal(params.get("reason"), 0);
        Integer state = p.getPaymeState();
        if (state != null && (state == STATE_CANCELLED || state == STATE_CANCELLED_AFTER)) {
            return cancelResult(p); // idempotent
        }
        p.setPaymeCancelTime(System.currentTimeMillis());
        p.setPaymeReason(reason);
        // Cancelling after a successful perform is a distinct state from before.
        p.setPaymeState((state != null && state == STATE_PERFORMED)
                ? STATE_CANCELLED_AFTER : STATE_CANCELLED);
        payments.save(p);
        billing.markFailed(p.getId());
        return cancelResult(p);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> checkTransaction(Map<String, Object> params) {
        Payment p = byTx(str(params.get("id")));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("create_time", nz(p.getPaymeCreateTime()));
        r.put("perform_time", nz(p.getPaymePerformTime()));
        r.put("cancel_time", nz(p.getPaymeCancelTime()));
        r.put("transaction", String.valueOf(p.getId()));
        r.put("state", state(p));
        r.put("reason", p.getPaymeReason());
        return r;
    }

    // ------------------------------------------------------------ helpers

    private Payment orderFromAccount(Map<String, Object> params) {
        Object account = params.get("account");
        if (!(account instanceof Map<?, ?> acc)) {
            throw new PaymeException(ERR_ORDER_NOT_FOUND, "Buyurtma ma'lumoti yo'q");
        }
        Long id = parseLong(str(acc.get(accountField)));
        if (id == null) {
            throw new PaymeException(ERR_ORDER_NOT_FOUND, "Buyurtma topilmadi");
        }
        return payments.findById(id)
                .orElseThrow(() -> new PaymeException(ERR_ORDER_NOT_FOUND, "Buyurtma topilmadi"));
    }

    private Payment byTx(String txId) {
        if (txId == null) {
            throw new PaymeException(ERR_TX_NOT_FOUND, "Tranzaksiya topilmadi");
        }
        return payments.findByPaymeTxId(txId)
                .orElseThrow(() -> new PaymeException(ERR_TX_NOT_FOUND, "Tranzaksiya topilmadi"));
    }

    private void requirePayable(Payment p, long amountTiyin) {
        if (p.getStatus() == PaymentStatus.PAID) {
            throw new PaymeException(ERR_ORDER_STATE, "Buyurtma allaqachon to'langan");
        }
        if (amountTiyin != p.getAmountUzs() * 100L) {
            throw new PaymeException(ERR_INVALID_AMOUNT, "Noto'g'ri summa");
        }
    }

    private static int state(Payment p) {
        return p.getPaymeState() == null ? 0 : p.getPaymeState();
    }

    private static long amount(Map<String, Object> params) {
        return longVal(params.get("amount"), -1);
    }

    private Map<String, Object> createResult(Payment p) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("create_time", nz(p.getPaymeCreateTime()));
        r.put("transaction", String.valueOf(p.getId()));
        r.put("state", state(p));
        return r;
    }

    private Map<String, Object> performResult(Payment p) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("transaction", String.valueOf(p.getId()));
        r.put("perform_time", nz(p.getPaymePerformTime()));
        r.put("state", state(p));
        return r;
    }

    private Map<String, Object> cancelResult(Payment p) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("transaction", String.valueOf(p.getId()));
        r.put("cancel_time", nz(p.getPaymeCancelTime()));
        r.put("state", state(p));
        return r;
    }

    private static Map<String, Object> one(String key, Object value) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put(key, value);
        return r;
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static long longVal(Object o, long def) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return o == null ? def : Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Long parseLong(String s) {
        try {
            return s == null ? null : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
