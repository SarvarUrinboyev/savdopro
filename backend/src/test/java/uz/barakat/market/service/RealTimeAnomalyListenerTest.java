package uz.barakat.market.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.barakat.market.domain.Sale;
import uz.barakat.market.repository.PaymentRepository;
import uz.barakat.market.repository.SaleRepository;
import uz.barakat.market.service.LedgerEvents.SalePosted;
import uz.barakat.market.service.LedgerEvents.SaleRefunded;

/**
 * Real-time anomaly trigger semantics: a committed sale/refund kicks one
 * intraday scan for that shop, a burst within the debounce window kicks only
 * one, and the disabled flag makes the listener inert.
 */
@ExtendWith(MockitoExtension.class)
class RealTimeAnomalyListenerTest {

    @Mock private AnomalyMonitorService monitor;
    @Mock private SaleRepository sales;
    @Mock private PaymentRepository payments;

    private RealTimeAnomalyListener listener;

    @BeforeEach
    void setUp() {
        listener = new RealTimeAnomalyListener(monitor, sales, payments, true, 60);
        listener.setExecutorForTests(Runnable::run); // synchronous for asserts
    }

    private static SaleRefunded refund(long shopId) {
        return new SaleRefunded(shopId, 5L, LocalDate.now(), "NAQD",
                BigDecimal.ONE, List.of(), "ref-1");
    }

    @Test
    void refundTriggersScanForItsShop() {
        listener.onRefund(refund(42L));
        verify(monitor).scanCurrentShop(LocalDate.now());
    }

    @Test
    void burstWithinDebounceWindowScansOnce() {
        listener.onRefund(refund(42L));
        listener.onRefund(refund(42L));
        listener.onRefund(refund(42L));
        verify(monitor, times(1)).scanCurrentShop(any());
    }

    @Test
    void distinctShopsScanIndependently() {
        listener.onRefund(refund(42L));
        listener.onRefund(refund(43L));
        verify(monitor, times(2)).scanCurrentShop(any());
    }

    @Test
    void saleResolvesShopThroughRepository() {
        Sale s = new Sale();
        s.setShopId(7L);
        when(sales.findById(11L)).thenReturn(Optional.of(s));

        listener.onSale(new SalePosted(11L));

        verify(monitor).scanCurrentShop(LocalDate.now());
    }

    @Test
    void disabledFlagMakesListenerInert() {
        RealTimeAnomalyListener off =
                new RealTimeAnomalyListener(monitor, sales, payments, false, 60);
        off.setExecutorForTests(Runnable::run);

        off.onRefund(refund(42L));

        verify(monitor, never()).scanCurrentShop(any());
    }
}
