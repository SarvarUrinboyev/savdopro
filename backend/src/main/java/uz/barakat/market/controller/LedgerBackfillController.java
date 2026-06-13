package uz.barakat.market.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.dto.AccountingDtos.BackfillResponse;
import uz.barakat.market.service.LedgerBackfillService;

/** One-shot replay of operational history into the ledger. */
@RestController
@RequestMapping("/api/accounting/backfill")
public class LedgerBackfillController {

    private final LedgerBackfillService service;

    public LedgerBackfillController(LedgerBackfillService service) {
        this.service = service;
    }

    @PostMapping
    public BackfillResponse run() {
        return service.run();
    }
}
