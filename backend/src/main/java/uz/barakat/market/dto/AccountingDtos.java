package uz.barakat.market.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import uz.barakat.market.domain.Currency;
import uz.barakat.market.domain.GlAccountType;
import uz.barakat.market.domain.JournalSource;
import uz.barakat.market.domain.NormalBalance;
import uz.barakat.market.domain.PeriodStatus;

/** Request/response records for the accounting module (grouped, à la PosDtos). */
public final class AccountingDtos {

    private AccountingDtos() {
    }

    // ----------------------------------------------------- Chart of Accounts

    public record GlAccountRequest(
            @NotBlank String code,
            @NotBlank String name,
            GlAccountType type,
            Long parentId,
            Boolean active,
            String description) {
    }

    public record GlAccountResponse(
            Long id, String code, String name, GlAccountType type,
            NormalBalance normalBalance, Long parentId, boolean system,
            boolean active, String description) {
    }

    // ------------------------------------------------------------- Journal

    public record JournalLineRequest(
            Long accountId, BigDecimal debit, BigDecimal credit,
            Currency currency, String memo) {
    }

    public record JournalEntryRequest(
            LocalDate entryDate, String memo, List<JournalLineRequest> lines) {
    }

    public record JournalLineResponse(
            Long id, Long accountId, String accountCode, String accountName,
            GlAccountType accountType, BigDecimal debit, BigDecimal credit,
            Currency currency, BigDecimal origAmount, String memo) {
    }

    public record JournalEntryResponse(
            Long id, LocalDate entryDate, String memo, JournalSource source,
            String sourceRef, boolean posted, String createdBy, Long reversedEntryId,
            BigDecimal totalDebit, BigDecimal totalCredit,
            LocalDateTime createdAt, List<JournalLineResponse> lines) {
    }

    public record JournalListResponse(
            LocalDate from, LocalDate to,
            BigDecimal totalDebit, BigDecimal totalCredit,
            List<JournalEntryResponse> entries) {
    }

    // ------------------------------------------------------- Trial balance

    public record TrialBalanceRow(
            Long accountId, String code, String name, GlAccountType type,
            BigDecimal debit, BigDecimal credit, BigDecimal balance) {
    }

    public record TrialBalanceResponse(
            LocalDate from, LocalDate to, List<TrialBalanceRow> rows,
            BigDecimal totalDebit, BigDecimal totalCredit, boolean balanced) {
    }

    // ---------------------------------------------- Profit & Loss statement

    public record StatementLine(String code, String name, BigDecimal amount) {
    }

    public record ProfitLossResponse(
            LocalDate from, LocalDate to,
            List<StatementLine> revenue, BigDecimal revenueTotal,
            List<StatementLine> cogs, BigDecimal cogsTotal, BigDecimal grossProfit,
            List<StatementLine> expenses, BigDecimal expenseTotal,
            BigDecimal netProfit) {
    }

    // --------------------------------------------------------- Balance sheet

    public record BalanceSheetResponse(
            LocalDate asOf,
            List<StatementLine> assets, BigDecimal assetTotal,
            List<StatementLine> liabilities, BigDecimal liabilityTotal,
            List<StatementLine> equity, BigDecimal equityCapitalTotal,
            BigDecimal retainedEarnings, BigDecimal netIncomeToDate,
            BigDecimal equityTotal, BigDecimal liabilitiesPlusEquity, boolean balanced) {
    }

    // ----------------------------------------------------- Cash flow (direct)

    public record CashFlowLine(String label, BigDecimal amount) {
    }

    public record CashFlowResponse(
            LocalDate from, LocalDate to,
            BigDecimal openingCash, BigDecimal closingCash, BigDecimal netChange,
            List<CashFlowLine> inflows, BigDecimal inflowTotal,
            List<CashFlowLine> outflows, BigDecimal outflowTotal) {
    }

    // ------------------------------------------------------------- Periods

    public record PeriodResponse(
            Long id, LocalDate periodStart, LocalDate periodEnd, PeriodStatus status,
            LocalDateTime closedAt, String closedBy, String note) {
    }

    public record ClosePeriodRequest(LocalDate periodStart, LocalDate periodEnd, String note) {
    }

    // ------------------------------------------------------------- Backfill

    public record BackfillResponse(int created, int skipped, String message) {
    }
}
