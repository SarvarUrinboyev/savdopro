package uz.barakat.license.auth;

/**
 * The raw fields Click posts on a Prepare (action=0) / Complete (action=1)
 * callback. Kept as strings because the MD5 {@code sign_string} is computed
 * over the exact values Click sent (notably {@code amount}, which may arrive
 * as {@code "99000.00"}) — reformatting them would break verification.
 * {@code merchantPrepareId} is null on the Prepare leg.
 */
public record ClickCallback(
        String clickTransId,
        String serviceId,
        String clickPaydocId,
        String merchantTransId,
        String merchantPrepareId,
        String amount,
        String action,
        String error,
        String errorNote,
        String signTime,
        String signString) {
}
