package uz.barakat.market.dto;

/**
 * Result of scanning a barcode. {@code barcode} is the canonical (GTIN) form the
 * product is stored under, not the raw scanned string — so a GS1 marking code's
 * per-unit serial doesn't make every scan look new.
 *
 * <p>When {@code found} is true the product already exists and the UI offers a
 * quick stock-in ("Kirim"); otherwise the barcode is unknown and the UI offers to
 * create a new product, pre-filling it from the national catalogue which the
 * browser queries directly (the hosted backend can't reach Uzbek gov endpoints).
 */
public record ScanResponse(
        boolean found,
        String barcode,
        ProductResponse product) {
}
