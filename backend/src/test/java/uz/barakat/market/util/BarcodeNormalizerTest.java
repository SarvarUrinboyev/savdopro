package uz.barakat.market.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Unit tests for canonicalising scanned barcodes and GS1 marking codes. */
class BarcodeNormalizerTest {

    // A real "ASL BELGISI" DataMatrix: (01)08907588001769 (21)<unique serial>.
    private static final String MARKING_CODE =
            "010890758800176921Z9VDRZVTVNCRR91UZF092dVVDaQ+1hW2o+JlPxript7q4W8vR2zGsxOrmv1lP1Jo=";

    @Test
    void extractsGtinFromGs1MarkingCode() {
        assertEquals("8907588001769", BarcodeNormalizer.normalize(MARKING_CODE));
    }

    @Test
    void differentSerialsOfSameProductCollapseToOneCode() {
        String unitA = "01089075880017692100000000000001";
        String unitB = "01089075880017692199999999999999";
        assertEquals(BarcodeNormalizer.normalize(unitA), BarcodeNormalizer.normalize(unitB));
        assertEquals("8907588001769", BarcodeNormalizer.normalize(unitA));
    }

    @Test
    void plainEan13MatchesTheDataMatrixGtin() {
        assertEquals(BarcodeNormalizer.normalize(MARKING_CODE),
                BarcodeNormalizer.normalize("8907588001769"));
    }

    @Test
    void stripsLeadingZerosSoGtin14AndEan13Agree() {
        assertEquals("12345678905", BarcodeNormalizer.normalize("00012345678905"));
        assertEquals("12345678905", BarcodeNormalizer.normalize("12345678905"));
    }

    @Test
    void stripsAimSymbologyIdentifier() {
        assertEquals("8907588001769", BarcodeNormalizer.normalize("]d2" + MARKING_CODE));
    }

    @Test
    void stripsGroupSeparatorControlChar() {
        String gs = String.valueOf((char) 0x1D);
        assertEquals("8907588001769",
                BarcodeNormalizer.normalize("0108907588001769" + gs + "21ABC123"));
    }

    @Test
    void blankAndNullBecomeEmpty() {
        assertEquals("", BarcodeNormalizer.normalize(null));
        assertEquals("", BarcodeNormalizer.normalize("   "));
    }

    @Test
    void keepsNonNumericCodesAsIs() {
        assertEquals("ABC-123-XYZ", BarcodeNormalizer.normalize("  ABC-123-XYZ  "));
    }

    @Test
    void gtinReturnsNumericFormForValidCodes() {
        assertEquals("8907588001769", BarcodeNormalizer.gtin(MARKING_CODE));
        assertEquals("8907588001769", BarcodeNormalizer.gtin("8907588001769"));
    }

    @Test
    void gtinIsNullForNonNumericOrOverlongCodes() {
        assertNull(BarcodeNormalizer.gtin("ABC-123"));
        assertNull(BarcodeNormalizer.gtin(""));
        // A 15-digit (serial-only) numeric code is longer than any GTIN.
        assertNull(BarcodeNormalizer.gtin("123456789012345"));
    }
}
