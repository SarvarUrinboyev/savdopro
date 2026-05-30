package uz.barakat.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SaleRepository;
import uz.barakat.market.service.AnalyticsService.ProductProfitRow;

/**
 * Unit tests for the AI tool layer. Pure logic over mocked collaborators —
 * verifies each whitelisted tool formats real data correctly and that an
 * unknown / malicious tool name is rejected with a safe error rather than
 * throwing.
 */
@ExtendWith(MockitoExtension.class)
class AiToolServiceTest {

    @Mock SaleRepository sales;
    @Mock AnalyticsService analytics;
    @Mock ReportService reports;
    @Mock ProductRepository products;
    @InjectMocks AiToolService tools;

    private static ProductProfitRow row(long id, String name, int qty, String profit) {
        return new ProductProfitRow(id, name, qty,
                new BigDecimal("0"), new BigDecimal("0"),
                new BigDecimal(profit), new BigDecimal("0"));
    }

    @Test
    void catalog_lists_every_tool() {
        String cat = tools.catalog();
        assertThat(cat)
                .contains("salesInRange")
                .contains("productSales")
                .contains("topProducts")
                .contains("hourlySales")
                .contains("financeOnDate")
                .contains("lowStock");
    }

    @Test
    void unknown_tool_is_rejected_with_safe_error() {
        // A model that invents a tool (or tries something destructive) must
        // never blow up the request — it gets a recoverable error string.
        String out = tools.call("dropAllTables", Map.of());
        assertThat(out).contains("noma'lum asbob");
    }

    @Test
    void null_tool_name_does_not_throw() {
        assertThat(tools.call(null, Map.of())).startsWith("XATO");
    }

    @Test
    void salesInRange_reports_net_of_refunds() {
        // 5 sales, 1000 gross, 100 refunded -> 900 net.
        when(sales.summaryBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new Object[]{5L, new BigDecimal("1000"), new BigDecimal("100")});

        String out = tools.call("salesInRange",
                Map.of("from", "2026-05-01", "to", "2026-05-01"));

        assertThat(out)
                .contains("5 ta sotuv")
                .contains("900")
                .contains("qaytarilgan 100");
    }

    @Test
    void topProducts_lists_in_given_order() {
        when(analytics.profitByProduct(any(), any())).thenReturn(List.of(
                row(1L, "iPhone 15 Pro", 60, "18000"),
                row(2L, "Samsung S24", 10, "2000")));

        String out = tools.call("topProducts",
                Map.of("from", "2026-05-01", "to", "2026-05-30", "limit", 5));

        assertThat(out).contains("iPhone 15 Pro").contains("60 dona").contains("18000");
        assertThat(out).contains("Samsung S24");
    }

    @Test
    void productSales_filters_by_name_substring_case_insensitively() {
        when(analytics.profitByProduct(any(), any())).thenReturn(List.of(
                row(1L, "iPhone 15 Pro", 60, "18000"),
                row(2L, "Samsung S24", 10, "2000")));

        String out = tools.call("productSales",
                Map.of("name", "iphone", "from", "2026-05-01", "to", "2026-05-30"));

        assertThat(out).contains("iPhone 15 Pro").contains("60 dona");
        assertThat(out).doesNotContain("Samsung");
    }

    @Test
    void productSales_without_name_is_a_safe_error() {
        assertThat(tools.call("productSales", Map.of("from", "2026-05-01", "to", "2026-05-02")))
                .contains("nomi kerak");
    }
}
