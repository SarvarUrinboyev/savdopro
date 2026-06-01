package uz.barakat.market.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.dto.CustomerDetailResponse;
import uz.barakat.market.dto.CustomerRequest;
import uz.barakat.market.dto.CustomerResponse;
import uz.barakat.market.dto.CustomerTransactionRequest;
import uz.barakat.market.service.CustomerService;
import uz.barakat.market.service.OnlinePaymentService;

/** REST API for customers ("Mijozlar") and their goods / payment ledger. */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService service;
    private final OnlinePaymentService onlinePayments;

    public CustomerController(CustomerService service, OnlinePaymentService onlinePayments) {
        this.service = service;
        this.onlinePayments = onlinePayments;
    }

    @GetMapping
    public List<CustomerResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public CustomerDetailResponse detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id,
                                   @Valid @RequestBody CustomerRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    /** Adds a ledger line (goods given or payment received). */
    @PostMapping("/{id}/transactions")
    public CustomerDetailResponse addTransaction(
            @PathVariable Long id,
            @Valid @RequestBody CustomerTransactionRequest request) {
        return service.addTransaction(id, request);
    }

    /** Adds several ledger lines at once (a basket of goods) - all or nothing. */
    @PostMapping("/{id}/transactions/batch")
    public CustomerDetailResponse addTransactions(
            @PathVariable Long id,
            @Valid @RequestBody List<CustomerTransactionRequest> requests) {
        return service.addTransactions(id, requests);
    }

    /** Edits a ledger line: corrects its amount / description / date / note. */
    @PutMapping("/{id}/transactions/{transactionId}")
    public CustomerDetailResponse updateTransaction(
            @PathVariable Long id,
            @PathVariable Long transactionId,
            @Valid @RequestBody CustomerTransactionRequest request) {
        return service.updateTransaction(id, transactionId, request);
    }

    @DeleteMapping("/{id}/transactions/{transactionId}")
    public CustomerDetailResponse deleteTransaction(@PathVariable Long id,
                                                    @PathVariable Long transactionId) {
        return service.deleteTransaction(id, transactionId);
    }

    /**
     * Burn N loyalty points and append a redeem ledger entry. The new
     * customer-with-balance snapshot comes back so the UI can re-render
     * the points pill without a separate re-fetch.
     */
    @PostMapping("/{id}/loyalty/redeem")
    public CustomerResponse redeemPoints(@PathVariable Long id,
                                         @Valid @RequestBody LoyaltyRedeemRequest body) {
        return service.redeemPoints(id, body.points());
    }

    public record LoyaltyRedeemRequest(@jakarta.validation.constraints.Positive long points) { }

    /**
     * Sends a one-off message to the customer over the best available
     * channel (linked Telegram bot, else SMS). {@code template} is
     * {@code DEBT}, {@code ORDER_READY} or {@code CUSTOM}. The response
     * tells the cashier which channel carried the message.
     */
    @PostMapping("/{id}/notify")
    public NotifyResponse notify(@PathVariable Long id, @RequestBody NotifyRequest body) {
        String channel = service.sendNotification(id, body.template(), body.text());
        return new NotifyResponse(channel);
    }

    public record NotifyRequest(String template, String text) { }

    public record NotifyResponse(String channel) { }

    /**
     * Sends a debt reminder to every customer who currently owes money,
     * over each one's best channel. Returns how many debtors were reminded
     * and the per-channel breakdown.
     */
    @PostMapping("/remind-debtors")
    public CustomerService.BulkReminderResult remindDebtors() {
        return service.remindAllDebtors();
    }

    /**
     * Online-payment context for a customer: outstanding debt (USD), the
     * suggested charge in UZS so'm, and which providers are configured.
     */
    @GetMapping("/{id}/pay-info")
    public PayInfoResponse payInfo(@PathVariable Long id) {
        return new PayInfoResponse(
                onlinePayments.debtUsd(id),
                onlinePayments.suggestedSom(id),
                onlinePayments.paymeEnabled(),
                onlinePayments.clickEnabled());
    }

    /**
     * Generates a hosted-checkout link for the customer's debt.
     * {@code provider} is "payme" or "click"; {@code amountSom} is the
     * charge in UZS so'm.
     */
    @PostMapping("/{id}/pay-link")
    public PayLinkResponse payLink(@PathVariable Long id, @RequestBody PayLinkRequest body) {
        String url = onlinePayments.generateLink(id, body.provider(), body.amountSom());
        return new PayLinkResponse(url, body.provider(), body.amountSom());
    }

    public record PayInfoResponse(java.math.BigDecimal debtUsd, long suggestedSom,
                                  boolean paymeEnabled, boolean clickEnabled) { }

    public record PayLinkRequest(String provider, long amountSom) { }

    public record PayLinkResponse(String url, String provider, long amountSom) { }
}
