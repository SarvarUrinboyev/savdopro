package uz.barakat.market.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.auth.JwtAuthFilter;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.service.TransferService;
import uz.barakat.market.service.TransferService.TransferRequest;
import uz.barakat.market.service.TransferService.TransferResponse;

/**
 * Cross-shop stock transfers. Lives on the local backend — both shops
 * are owned by the same desktop install (one customer account).
 */
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    @GetMapping
    public List<TransferResponse> list(HttpServletRequest http) {
        return service.recent(requireAccount(http));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse create(@Valid @RequestBody TransferRequest request,
                                   HttpServletRequest http) {
        Long accountId = requireAccount(http);
        String username = (String) http.getAttribute("savdopro.username");
        return service.create(accountId, username, request);
    }

    private static Long requireAccount(HttpServletRequest http) {
        Long id = (Long) http.getAttribute(JwtAuthFilter.ATTR_ACCOUNT_ID);
        if (id == null) throw new BadRequestException("Sessiya yaroqsiz");
        return id;
    }
}
