package uz.barakat.market.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonicalises a scanned code so every physical unit of the same product
 * maps to one stable string.
 *
 * <p>Uzbekistan's mandatory digital marking ("ASL BELGISI") prints a GS1
 * DataMatrix on each item. Its element string looks like
 * {@code (01)<14-digit GTIN>(21)<unique serial>...} — only the GTIN names the
 * product; the serial after {@code (21)} is different on every single unit. If
 * the raw string is stored as the barcode, a second identical item never
 * matches the first and the app keeps treating it as a brand-new product.
 *
 * <p>This helper reduces such codes to their GTIN and drops leading zeros, so a
 * DataMatrix scan and the plain EAN-13/UPC printed on the same box resolve to
 * the same canonical value. Plain barcodes pass through (zero-trimmed); codes
 * that aren't GS1/numeric are returned trimmed and unchanged.
 */
public final class BarcodeNormalizer {

    private static final Pattern PAREN_GS1_GTIN = Pattern.compile("\\(01\\)(\\d{14})");
    private static final Pattern URL_GTIN_PARAM =
            Pattern.compile("(?i)(?:^|[?&#;])(?:gtin|barcode|ean|upc|code)=([0-9]{8,14})(?:\\b|&|$)");

    private BarcodeNormalizer() {
    }

    /** Canonical, storable form of a scanned code; empty string when blank. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        // Drop the GS1 FNC1 / group-separator control char (ASCII 29) some
        // scanners emit between AI fields, then trim surrounding whitespace.
        String s = raw.replaceAll("\\x1d", "").trim();
        // Strip a leading AIM symbology identifier (e.g. "]d2", "]C1", "]Q3").
        if (s.length() > 3 && s.charAt(0) == ']') {
            s = s.substring(3);
        }
        if (s.isEmpty()) {
            return "";
        }

        Matcher urlGtin = URL_GTIN_PARAM.matcher(s);
        if (urlGtin.find()) {
            s = urlGtin.group(1);
        } else {
            Matcher parenGs1 = PAREN_GS1_GTIN.matcher(s);
            if (parenGs1.find()) {
                s = "01" + parenGs1.group(1) + s.substring(parenGs1.end());
            }
        }

        String core = s;
        // GS1 element string that opens with AI (01) + a 14-digit GTIN.
        if (s.length() >= 16 && s.startsWith("01") && isDigits(s.substring(2, 16))) {
            core = s.substring(2, 16);
        }
        if (isDigits(core)) {
            String stripped = core.replaceFirst("^0+", "");
            return stripped.isEmpty() ? "0" : stripped;
        }
        return core;
    }

    /**
     * The numeric GTIN to query an external product catalogue with, or
     * {@code null} when the code isn't a plausible GTIN (non-numeric or too
     * long to be one).
     */
    public static String gtin(String raw) {
        String n = normalize(raw);
        if (n.isEmpty() || n.length() > 14 || !isDigits(n)) {
            return null;
        }
        return n;
    }

    /**
     * The best single numeric code to query external product databases with, or
     * {@code null} when the code isn't usable as a GTIN (non-numeric).
     *
     * <p>Unlike {@link #gtin(String)} this does not give up on an over-long
     * numeric code: a scan longer than any GTIN is most often an EAN-13 carrying
     * a 2- or 5-digit supplement (the add-on printed beside magazines, books and
     * some imports), or a scanner that appended extra digits. Rather than refuse
     * to look it up — which leaves the warehouse form blank — fall back to the
     * leading EAN-13 so the databases still get a plausible code.
     */
    public static String lookupGtin(String raw) {
        String n = normalize(raw);
        if (n.isEmpty() || !isDigits(n)) {
            return null;
        }
        if (n.length() <= 14) {
            return n;
        }
        return n.substring(0, 13);
    }

    // ASCII 0-9 only — deliberately NOT Character.isDigit(), which also accepts
    // non-ASCII Unicode digits the JS twin (\d) rejects. Barcodes are ASCII, and
    // both sides must canonicalise identically.
    private static boolean isDigits(String v) {
        if (v.isEmpty()) {
            return false;
        }
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
