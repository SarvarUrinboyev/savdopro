package uz.barakat.market.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.dto.AccountingDtos.BalanceSheetResponse;
import uz.barakat.market.dto.AccountingDtos.CashFlowResponse;
import uz.barakat.market.dto.AccountingDtos.ProfitLossResponse;
import uz.barakat.market.dto.AccountingDtos.TrialBalanceResponse;
import uz.barakat.market.service.FinancialStatementService;

/** Financial statements derived from the ledger. */
@RestController
@RequestMapping("/api/accounting/reports")
public class AccountingReportController {

    private final FinancialStatementService service;

    public AccountingReportController(FinancialStatementService service) {
        this.service = service;
    }

    @GetMapping("/trial-balance")
    public TrialBalanceResponse trialBalance(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate[] r = range(from, to);
        return service.trialBalance(r[0], r[1]);
    }

    @GetMapping("/profit-loss")
    public ProfitLossResponse profitLoss(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate[] r = range(from, to);
        return service.profitLoss(r[0], r[1]);
    }

    @GetMapping("/balance-sheet")
    public BalanceSheetResponse balanceSheet(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.balanceSheet(asOf != null ? asOf : LocalDate.now());
    }

    @GetMapping("/cash-flow")
    public CashFlowResponse cashFlow(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate[] r = range(from, to);
        return service.cashFlow(r[0], r[1]);
    }

    /** Defaults to the current month, like the Menejment page. */
    private static LocalDate[] range(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        return new LocalDate[] {
                from != null ? from : today.withDayOfMonth(1),
                to != null ? to : today };
    }
}
