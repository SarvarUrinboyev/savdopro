package uz.barakat.market.dto;

/**
 * A read-only suggestion from the global barcode lookup, used by the warehouse
 * scan modal as a fallback when the national catalogue has nothing for an
 * unknown code.
 *
 * <p>It only suggests — creating the product is a separate step. When neither
 * source knows the code (or the network failed) {@code found} is false and the
 * remaining fields are null; with {@code default-property-inclusion=non_null}
 * that serialises to exactly {@code {"found":false}}.
 *
 * @param source which database supplied the hit (null on a miss).
 */
public record BarcodeLookupResponse(
        boolean found,
        String name,
        String suggestedCategory,
        String source) {

    /** A miss — serialises to {@code {"found":false}}. */
    public static BarcodeLookupResponse notFound() {
        return new BarcodeLookupResponse(false, null, null, null);
    }
}
