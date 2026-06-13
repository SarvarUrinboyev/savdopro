package uz.barakat.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Records for the bank/payments reconciliation view. */
public final class ReconciliationDtos {

    private ReconciliationDtos() {
    }

    /** One online (Click/Payme) payment + how it reconciles to the debt ledger. */
    public record OnlineRow(
            Long id, String provider, String providerTxnId,
            Long customerId, String customerName, BigDecimal amountUsd,
            String status,           // MATCHED | PENDING | UNRECONCILED | CANCELLED
            Long performedAtMs, String note) {
    }

    public record OnlineSummary(
            int total, int matched, int pending, int unreconciled, int cancelled,
            BigDecimal matchedUsd, BigDecimal unreconciledUsd) {
    }

    /** Card-terminal settlement vs POS card sales for one day (in UZS so'm). */
    public record TerminalDay(
            LocalDate date, BigDecimal terminalSom, BigDecimal posCardSom,
            BigDecimal diffSom, String status) {   // BALANCED | DISCREPANCY
    }

    public record TerminalSummary(
            BigDecimal terminalTotalSom, BigDecimal posCardTotalSom,
            BigDecimal diffSom, int discrepancyDays) {
    }

    public record ReconciliationResponse(
            LocalDate from, LocalDate to,
            List<OnlineRow> online, OnlineSummary onlineSummary,
            List<TerminalDay> terminal, TerminalSummary terminalSummary) {
    }

    public record CreditResult(boolean credited, String message) {
    }
}
