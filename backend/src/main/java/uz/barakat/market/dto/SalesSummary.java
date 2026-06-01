package uz.barakat.market.dto;

import java.math.BigDecimal;

/**
 * Aggregated sales figures for a single day, used by the owner's daily
 * Telegram report. {@code net} is gross takings minus refunds (matches the
 * Sales page); {@code cogs} is the cost of the units actually sold (sold
 * quantity x current purchase price), so {@code profit = net - cogs} is an
 * estimate — current cost is used because the historical cost is not stored
 * on each sale line.
 */
public record SalesSummary(
        long count,
        BigDecimal gross,
        BigDecimal refunded,
        BigDecimal net,
        BigDecimal cogs,
        BigDecimal profit) {

    public static final SalesSummary EMPTY = new SalesSummary(
            0L, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
}
