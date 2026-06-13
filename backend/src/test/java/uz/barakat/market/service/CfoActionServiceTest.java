package uz.barakat.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uz.barakat.market.domain.Product;
import uz.barakat.market.repository.ProductRepository;

/**
 * The AI CFO's free-text {@code ACTION} lines must be parsed, resolved against
 * this shop's data, and stripped from the visible answer — and unresolvable or
 * absent actions must never surface a button (nor hit the DB needlessly).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CfoActionServiceTest {

    @Mock ProductRepository products;
    @Mock CustomerService customers;
    @InjectMocks CfoActionService svc;

    private static Product product(long id, String name, String cost, String price) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPurchasePrice(new BigDecimal(cost));
        p.setSalePrice(new BigDecimal(price));
        return p;
    }

    @Test
    void parsesResolvesAndStripsOrderAction() {
        when(products.findAllByOrderByNameAsc())
                .thenReturn(List.of(product(7L, "Coca-Cola 0.5L", "2.00", "3.00")));

        var ex = svc.extract("Tovar tugayapti.\nACTION ORDER | Coca | 50");

        assertThat(ex.text()).contains("tugayapti").doesNotContain("ACTION");
        assertThat(ex.actions()).hasSize(1);
        var a = ex.actions().get(0);
        assertThat(a.type()).isEqualTo("ORDER");
        assertThat(a.params()).containsEntry("productId", 7L).containsEntry("qty", 50);
        assertThat((BigDecimal) a.params().get("estAmountUsd")).isEqualByComparingTo("100.00");
    }

    @Test
    void priceActionComputesNewPrice() {
        when(products.findAllByOrderByNameAsc())
                .thenReturn(List.of(product(3L, "Non", "0.50", "1.00")));

        var ex = svc.extract("Narxni oshirish mumkin.\nACTION PRICE | Non | 10");

        assertThat(ex.actions()).hasSize(1);
        var a = ex.actions().get(0);
        assertThat(a.type()).isEqualTo("PRICE");
        assertThat((BigDecimal) a.params().get("newPriceUsd")).isEqualByComparingTo("1.10");
    }

    @Test
    void dropsUnresolvableProduct() {
        when(products.findAllByOrderByNameAsc()).thenReturn(List.of());
        var ex = svc.extract("ACTION DISCOUNT | Yo'q mahsulot | 10");
        assertThat(ex.actions()).isEmpty();
        assertThat(ex.text()).doesNotContain("ACTION");
    }

    @Test
    void noActionLinesTouchesNothing() {
        var ex = svc.extract("Oddiy javob, hech qanday amal yo'q.");
        assertThat(ex.actions()).isEmpty();
        assertThat(ex.text()).contains("Oddiy javob");
        verifyNoInteractions(products, customers);
    }
}
