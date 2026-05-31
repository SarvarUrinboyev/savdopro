package uz.barakat.market.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.barakat.market.auth.ShopService.CreateShopRequest;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.repository.ShopRepository;

/**
 * The plan's shop limit (carried in the JWT as maxShops) is enforced when a
 * new shop is created: at the cap the create is refused and nothing is saved.
 */
@ExtendWith(MockitoExtension.class)
class ShopServiceMaxShopsTest {

    @Mock private ShopRepository shops;
    @InjectMocks private ShopService service;

    private static CreateShopRequest req() {
        return new CreateShopRequest("Yangi do'kon", null, null, null, null, null);
    }

    @Test
    void createRejectedAtPlanShopLimit() {
        when(shops.countByAccountId(7L)).thenReturn(1L); // already at the cap

        assertThatThrownBy(() -> service.create(7L, 1, req()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chegarasi");

        verify(shops, never()).save(any());
    }
}
