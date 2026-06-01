package uz.barakat.market.payment;

/**
 * A Payme JSON-RPC error: carries the numeric Payme error code so the
 * controller can render the {@code error: {code, message}} envelope the
 * Payme servers expect.
 */
public class PaymeException extends RuntimeException {

    // Standard Payme merchant-API error codes.
    public static final int INVALID_AMOUNT = -31001;
    public static final int TXN_NOT_FOUND = -31003;
    public static final int CANNOT_PERFORM = -31008;
    public static final int CANNOT_CANCEL = -31007;
    public static final int CUSTOMER_NOT_FOUND = -31050; // custom account-field range

    private final int code;

    public PaymeException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
