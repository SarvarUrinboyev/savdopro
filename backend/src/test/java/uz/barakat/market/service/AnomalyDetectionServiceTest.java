package uz.barakat.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uz.barakat.market.config.AnomalyProperties;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.Sale;
import uz.barakat.market.domain.SaleItem;
import uz.barakat.market.repository.ExpenseRepository;
import uz.barakat.market.repository.HomeExpenseRepository;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SaleRepository;
import uz.barakat.market.repository.SaleRepository.CashierRefundRow;
import uz.barakat.market.repository.ShiftRepository;
import uz.barakat.market.service.AnomalyDetectionService.Candidate;

/** Unit tests for the five deterministic anomaly detectors. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnomalyDetectionServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 10);

    @Mock private SaleRepository sales;
    @Mock private ProductRepository products;
    @Mock private ExpenseRepository expenses;
    @Mock private HomeExpenseRepository homeExpenses;
    @Mock private ShiftRepository shifts;
    @Mock private BalanceService balanceService;
    @Mock private MoneyConverter converter;

    private final AnomalyProperties props = new AnomalyProperties(
            true, 15, 30, 4, new BigDecimal("50"), 3, 23, 6, 3.0, 3,
            new BigDecimal("5"), 2.5, 5, 28, 7);

    private AnomalyDetectionService svc;

    @BeforeEach
    void setup() {
        svc = new AnomalyDetectionService(sales, products, expenses, homeExpenses,
                shifts, balanceService, converter, props, new ObjectMapper());
        // Expenses are treated as already-USD in tests (no FX) unless overridden.
        lenient().when(converter.toUsd(any(), any()))
                .thenAnswer(inv -> inv.getArgument(0) == null ? BigDecimal.ZERO : inv.getArgument(0));
        // Quiet defaults so each test only stubs what its detector needs.
        lenient().when(sales.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        lenient().when(sales.findRefundedBetween(any(), any())).thenReturn(List.of());
        lenient().when(sales.cashierRefundStats(any(), any())).thenReturn(List.of());
        lenient().when(expenses.findByDateOrderByIdDesc(any())).thenReturn(List.of());
        lenient().when(homeExpenses.findByDateOrderByIdDesc(any())).thenReturn(List.of());
        lenient().when(shifts.findAllByOrderByOpenedAtDesc()).thenReturn(List.of());
        lenient().when(balanceService.startingCash(any())).thenReturn(BigDecimal.ZERO);
        lenient().when(products.findAllById(any())).thenReturn(List.of());
    }

    // ======================================================= 1. till-negative

    @Test
    void tillNegativeFiresCriticalWhenCashRefundsExceedStartingPlusCashSales() {
        when(balanceService.startingCash(DAY)).thenReturn(new BigDecimal("0"));
        stubToday(List.of(cashSale(1, "30")));            // +30 cash in
        Sale refunded = cashSale(2, "0");
        refunded.setRefundedTotalUzs(new BigDecimal("100")); // -100 cash refund today
        refunded.setRefundedAt(at(15));
        when(sales.findRefundedBetween(any(), any())).thenReturn(List.of(refunded));

        Candidate c = find(svc.detect(DAY), "till-negative");
        assertNotNull(c, "drawer goes negative: 0 + 30 − 100 = −70");
        assertEquals("critical", c.severity()); // |−70| >= largeRefundMinUsd(50)
    }

    @Test
    void tillNegativeNoFireWhenPositive() {
        when(balanceService.startingCash(DAY)).thenReturn(new BigDecimal("100"));
        stubToday(List.of(cashSale(1, "20")));
        Sale refunded = cashSale(2, "0");
        refunded.setRefundedTotalUzs(new BigDecimal("50"));
        refunded.setRefundedAt(at(15));
        when(sales.findRefundedBetween(any(), any())).thenReturn(List.of(refunded));

        assertNull(find(svc.detect(DAY), "till-negative")); // 100 + 20 − 50 = 70
    }

    @Test
    void tillNegativeIgnoresCardSalesAndRefunds() {
        when(balanceService.startingCash(DAY)).thenReturn(new BigDecimal("0"));
        Sale cardSale = sale(1, "ali", at(12), "200");
        cardSale.setPaymentMethod("KARTA"); // not cash → doesn't fund the drawer
        stubToday(List.of(cardSale));
        Sale cardRefund = sale(2, "ali", at(12), "0");
        cardRefund.setPaymentMethod("KARTA");
        cardRefund.setRefundedTotalUzs(new BigDecimal("100"));
        cardRefund.setRefundedAt(at(15));
        when(sales.findRefundedBetween(any(), any())).thenReturn(List.of(cardRefund));

        assertNull(find(svc.detect(DAY), "till-negative"),
                "card sales/refunds don't move the cash drawer");
    }

    // ====================================================== 2. below-cost daily

    @Test
    void belowCostDailyFiresWhenAggregateLossAtOrAboveFloor() {
        Sale s = sale(1, "ali", at(12), "10");
        s.addItem(item(7L, 2, "10")); // eff unit 5 < cost 10 → loss 10
        stubToday(List.of(s));
        when(products.findAllById(any())).thenReturn(List.of(product(7L, "10")));

        Candidate c = find(svc.detect(DAY), "below-cost-daily");
        assertNotNull(c);
        assertEquals("warn", c.severity()); // 10 < largeRefundMinUsd(50)
    }

    @Test
    void belowCostDailyNoFireBelowFloor() {
        Sale s = sale(1, "ali", at(12), "8");
        s.addItem(item(7L, 1, "8")); // eff 8 < cost 10 → loss 2 < floor 5
        stubToday(List.of(s));
        when(products.findAllById(any())).thenReturn(List.of(product(7L, "10")));

        assertNull(find(svc.detect(DAY), "below-cost-daily"));
    }

    // ============================================================ 3. refunds

    @Test
    void refundRateWarnsAtThresholdAndEscalatesToCritical() {
        Sale warn = sale(1, "ali", at(12), "100");
        warn.setRefundedTotalUzs(new BigDecimal("20")); // 20%
        stubToday(List.of(warn));
        assertEquals("warn", find(svc.detect(DAY), "refund-rate").severity());

        Sale crit = sale(1, "ali", at(12), "100");
        crit.setRefundedTotalUzs(new BigDecimal("40")); // 40%
        stubToday(List.of(crit));
        assertEquals("critical", find(svc.detect(DAY), "refund-rate").severity());
    }

    @Test
    void refundBurstFiresWhenManyRefundsInOneHour() {
        List<Sale> refunded = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Sale s = sale(i, "ali", at(9), "10");
            s.setRefundedAt(at(14));
            s.setRefundedTotalUzs(new BigDecimal("5"));
            refunded.add(s);
        }
        when(sales.findRefundedBetween(any(), any())).thenReturn(refunded);

        assertNotNull(find(svc.detect(DAY), "refund-burst"));
    }

    @Test
    void refundLargeFiresForSingleBigRefund() {
        Sale s = sale(42, "ali", at(9), "200");
        s.setRefundedAt(at(15));
        s.setRefundedTotalUzs(new BigDecimal("60")); // >= 50
        when(sales.findRefundedBetween(any(), any())).thenReturn(List.of(s));

        Candidate c = find(svc.detect(DAY), "refund-large");
        assertNotNull(c);
        assertTrue(c.message().contains("#42"));
    }

    @Test
    void refundStaleFiresWhenRefundedLongAfterSale() {
        Sale s = sale(7, "ali", DAY.minusDays(5).atTime(10, 0), "30");
        s.setRefundedAt(at(10)); // 5 days after sale >= refundLateDays(3)
        s.setRefundedTotalUzs(new BigDecimal("30"));
        when(sales.findRefundedBetween(any(), any())).thenReturn(List.of(s));

        assertNotNull(find(svc.detect(DAY), "refund-stale"));
    }

    // ======================================================= 4. night spike

    @Test
    void nightSpikeFiresWhenTonightFarAboveBaseline() {
        List<Sale> tonight = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            tonight.add(sale(i, "ali", at(23), "10")); // hour 23 is night
        }
        stubToday(tonight);
        // Baseline: 10 active days, only 2 night sales total → mean night count 0.2.
        List<Sale> baseline = new ArrayList<>();
        for (int d = 1; d <= 10; d++) {
            baseline.add(sale(100 + d, "ali", DAY.minusDays(d).atTime(12, 0), "10"));
        }
        baseline.add(sale(200, "ali", DAY.minusDays(3).atTime(2, 0), "10"));  // night
        baseline.add(sale(201, "ali", DAY.minusDays(5).atTime(23, 0), "10")); // night
        stubBaseline(baseline);

        assertNotNull(find(svc.detect(DAY), "night-spike"), "5 tonight vs ~0.2 baseline is a spike");
    }

    @Test
    void nightSpikeNoFireBelowMinCount() {
        List<Sale> tonight = List.of(sale(1, "ali", at(23), "10"), sale(2, "ali", at(1), "10"));
        stubToday(tonight); // only 2 < nightSpikeMinCount(3)

        assertNull(find(svc.detect(DAY), "night-spike"));
    }

    @Test
    void nightSpikeNoFireWhenBaselineTooThin() {
        List<Sale> tonight = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            tonight.add(sale(i, "ali", at(23), "10"));
        }
        stubToday(tonight);
        // Only 3 active baseline days (< minBaselineDays 7) → cannot call it a spike.
        List<Sale> baseline = List.of(
                sale(101, "ali", DAY.minusDays(1).atTime(12, 0), "10"),
                sale(102, "ali", DAY.minusDays(2).atTime(12, 0), "10"),
                sale(103, "ali", DAY.minusDays(3).atTime(12, 0), "10"));
        stubBaseline(baseline);

        assertNull(find(svc.detect(DAY), "night-spike"));
    }

    // ===================================================== 5. cashier patterns

    @Test
    void cashierAnomalyFiresForRefundRateOutlierVsPeerMedian() {
        when(sales.cashierRefundStats(any(), any())).thenReturn(List.of(
                refRow("ali", 10, "1000", "400"),   // 40% — outlier
                refRow("vali", 10, "1000", "50"),   // 5%
                refRow("guli", 10, "1000", "50")));  // 5% → median 5, threshold 12.5

        Candidate c = find(svc.detect(DAY), "cashier-anomaly");
        assertNotNull(c);
        assertTrue(c.message().contains("ali"));
    }

    @Test
    void cashierAnomalySkipsCashiersWithTooFewReceipts() {
        when(sales.cashierRefundStats(any(), any())).thenReturn(List.of(
                refRow("ali", 3, "300", "200"),     // 67% but only 3 receipts (< 5) → ignored
                refRow("vali", 10, "1000", "50"),
                refRow("guli", 10, "1000", "50")));

        assertNull(find(svc.detect(DAY), "cashier-anomaly"));
    }

    // ----------------------------------------------------------------- helpers

    private void stubToday(List<Sale> today) {
        when(sales.findByCreatedAtBetweenOrderByCreatedAtDesc(
                DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay())).thenReturn(today);
    }

    private void stubBaseline(List<Sale> baseline) {
        when(sales.findByCreatedAtBetweenOrderByCreatedAtDesc(
                DAY.minusDays(props.baselineDays()).atStartOfDay(), DAY.atStartOfDay())).thenReturn(baseline);
    }

    private static LocalDateTime at(int hour) {
        return DAY.atTime(hour, 0);
    }

    private static Sale cashSale(long id, String total) {
        Sale s = sale(id, "ali", at(12), total);
        s.setPaymentMethod("NAQD");
        return s;
    }

    private static Sale sale(long id, String cashier, LocalDateTime createdAt, String total) {
        Sale s = new Sale();
        s.setId(id);
        s.setCashier(cashier);
        s.setCreatedAt(createdAt);
        s.setTotalUzs(new BigDecimal(total));
        s.setRefundedTotalUzs(BigDecimal.ZERO);
        return s;
    }

    private static SaleItem item(long productId, int qty, String lineTotal) {
        SaleItem it = new SaleItem();
        it.setProductId(productId);
        it.setProductName("p" + productId);
        it.setQuantity(qty);
        it.setLineTotalUzs(new BigDecimal(lineTotal));
        return it;
    }

    private static Product product(long id, String cost) {
        Product p = new Product();
        p.setId(id);
        p.setName("p" + id);
        p.setPurchasePrice(new BigDecimal(cost));
        return p;
    }

    private static CashierRefundRow refRow(String cashier, long receipts, String gross, String refunded) {
        return new CashierRefundRow() {
            public String getCashier() {
                return cashier;
            }

            public long getReceipts() {
                return receipts;
            }

            public BigDecimal getGross() {
                return new BigDecimal(gross);
            }

            public BigDecimal getRefunded() {
                return new BigDecimal(refunded);
            }
        };
    }

    private static Candidate find(List<Candidate> list, String code) {
        return list.stream().filter(c -> c.code().equals(code)).findFirst().orElse(null);
    }
}
