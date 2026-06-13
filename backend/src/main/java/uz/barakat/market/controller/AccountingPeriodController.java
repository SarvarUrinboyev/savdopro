package uz.barakat.market.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.dto.AccountingDtos.ClosePeriodRequest;
import uz.barakat.market.dto.AccountingDtos.PeriodResponse;
import uz.barakat.market.service.AccountingPeriodService;

/** Accounting periods — close (lock) and re-open. */
@RestController
@RequestMapping("/api/accounting/periods")
public class AccountingPeriodController {

    private final AccountingPeriodService service;

    public AccountingPeriodController(AccountingPeriodService service) {
        this.service = service;
    }

    @GetMapping
    public List<PeriodResponse> list() {
        return service.list();
    }

    @PostMapping("/close")
    public PeriodResponse close(@RequestBody ClosePeriodRequest request) {
        return service.close(request);
    }

    @PostMapping("/{id}/reopen")
    public PeriodResponse reopen(@PathVariable Long id) {
        return service.reopen(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
