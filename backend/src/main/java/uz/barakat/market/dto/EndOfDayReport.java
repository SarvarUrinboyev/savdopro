package uz.barakat.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Full end-of-day report shown on the Shift Close page and sent to
 * Telegram. Mirrors section 2.7 of the specification.
 */
public record EndOfDayReport(
        LocalDate date,

        // SAVDO (bugungi sotuv, qaytarish, taxminiy foyda)
        SalesSummary sales,

        // SUPERMARKET XARAJATI
        BigDecimal marketKassa,
        BigDecimal marketNaqd,
        BigDecimal marketKarta,
        BigDecimal marketTotal,

        // UY XARAJATLARI
        BigDecimal homeKassa,
        BigDecimal homeNaqd,
        BigDecimal homeKarta,
        BigDecimal homeTotal,

        // KASSA HOLATI
        BigDecimal startingCash,
        BigDecimal cashOut,
        BigDecimal estimatedCash,

        // QARZLAR
        BigDecimal myDebtTotal,
        BigDecimal customerDebtTotal,

        // ERTAGA KELADI / KELMAGAN
        List<OrderResponse> tomorrowOrders,
        BigDecimal tomorrowOrdersTotal,
        List<OrderResponse> overdueOrders,

        // detail
        List<ExpenseResponse> expenses,
        List<HomeExpenseResponse> homeExpenses,

        // formatted 80mm receipt text
        String receiptText) {
}
