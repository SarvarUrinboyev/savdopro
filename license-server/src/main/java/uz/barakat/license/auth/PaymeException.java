package uz.barakat.license.auth;

/**
 * A Payme JSON-RPC error. {@link #code()} is the Payme / JSON-RPC error code
 * (e.g. -31001 invalid amount, -31003 transaction not found, -31008 unable to
 * perform, -31050..-31099 merchant-defined order errors); {@link PaymeController}
 * renders it as the {@code error} object Payme expects. Thrown by
 * {@link PaymeService} to abort a method.
 */
public class PaymeException extends RuntimeException {

    private final int code;

    public PaymeException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
