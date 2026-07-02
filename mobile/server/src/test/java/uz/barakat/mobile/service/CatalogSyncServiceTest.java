package uz.barakat.mobile.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.barakat.mobile.repository.CategoryRepository;
import uz.barakat.mobile.repository.ProductRepository;

/**
 * Narx konvertatsiyasi POS(USD) → mobil(so'm): kurs × markup, 100 so'mga
 * HALF_UP yaxlitlash. Sinxronning qolgan qismi integratsion xulq (HTTP +
 * upsert) — u staging'da real API bilan tekshiriladi.
 */
@ExtendWith(MockitoExtension.class)
class CatalogSyncServiceTest {

    @Mock private ProductRepository products;
    @Mock private CategoryRepository categories;

    private CatalogSyncService service(String rate, String markup) {
        return new CatalogSyncService(products, categories, false, "", "",
                new BigDecimal(rate), new BigDecimal(markup));
    }

    @Test
    void convertsUsdToRoundedSom() {
        // 1.25 USD * 12800 = 16000 so'm — allaqachon yaxlit.
        assertThat(service("12800", "1.0").usdToSom(new BigDecimal("1.25")))
                .isEqualTo(16_000L);
    }

    @Test
    void roundsToNearestHundredSom() {
        // 0.87 * 12650 = 11005.5 -> 11000 (100 ga HALF_UP)
        assertThat(service("12650", "1.0").usdToSom(new BigDecimal("0.87")))
                .isEqualTo(11_000L);
    }

    @Test
    void appliesRetailMarkup() {
        // 1 USD * 12800 * 1.15 = 14720 -> 14700
        assertThat(service("12800", "1.15").usdToSom(BigDecimal.ONE))
                .isEqualTo(14_700L);
    }

    @Test
    void nullPriceBecomesZero() {
        assertThat(service("12800", "1.0").usdToSom(null)).isZero();
    }
}
