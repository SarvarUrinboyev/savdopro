package uz.barakat.market.dto;

import java.math.BigDecimal;

/** One cashier's performance over a window: receipts, net takings, average check. */
public record CashierStat(String cashier, long receipts, BigDecimal net, BigDecimal avgReceipt) {
}
