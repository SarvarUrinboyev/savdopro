package uz.barakat.license.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uz.barakat.license.auth.AuthDtos.CheckoutRequest;
import uz.barakat.license.auth.AuthDtos.CheckoutResponse;
import uz.barakat.license.auth.AuthDtos.PaymentView;
import uz.barakat.license.auth.AuthDtos.SubscriptionStatusResponse;
import uz.barakat.license.auth.AuthDtos.WebhookRequest;
import uz.barakat.license.domain.Payment;
import uz.barakat.license.domain.SubscriptionPlan;
import uz.barakat.license.exception.BadRequestException;

/**
 * In-app billing for the current account: subscription status, plan checkout,
 * payment history, and the PSP confirmation webhook. The account is read from
 * the JWT; only the webhook is public (PSPs can't carry a user token).
 */
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final AuthService service;
    private final BillingService billing;
    private final String webhookSecret;

    public BillingController(AuthService service, BillingService billing,
                             @Value("${billing.webhook.secret:}") String webhookSecret) {
        this.service = service;
        this.billing = billing;
        this.webhookSecret = webhookSecret;
    }

    @GetMapping("/status")
    public SubscriptionStatusResponse status(HttpServletRequest request) {
        return service.subscriptionStatus(requireUserId(request));
    }

    @PostMapping("/checkout")
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest req,
                                     HttpServletRequest request) {
        Long accountId = requireAccountId(request);
        SubscriptionPlan plan = parsePlan(req.plan());
        int months = (req.months() == null) ? 1 : req.months();
        Payment p = billing.startCheckout(accountId, plan, months, "MANUAL");
        // A real PSP adapter returns its hosted-checkout URL here; until then we
        // return a placeholder the frontend can show / poll.
        return new CheckoutResponse(p.getId(), p.getAmountUzs(), "/billing/pay/" + p.getId());
    }

    @GetMapping("/payments")
    public List<PaymentView> payments(HttpServletRequest request) {
        return billing.history(requireAccountId(request)).stream()
                .map(BillingController::toView)
                .toList();
    }

    /**
     * PSP payment-confirmation webhook. PUBLIC (a PSP can't carry a user JWT) —
     * guarded by a shared-secret header for now. A real Payme/Click adapter
     * replaces this with the provider's own signature verification.
     */
    @PostMapping("/webhook")
    public void webhook(@RequestBody WebhookRequest req,
                        @RequestHeader(value = "X-Webhook-Secret", required = false) String secret) {
        if (webhookSecret == null || webhookSecret.isBlank() || !webhookSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Webhook imzosi noto'g'ri");
        }
        billing.confirmPayment(req.paymentId(), req.externalId());
    }

    private Long requireUserId(HttpServletRequest request) {
        Object uid = request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        if (uid == null) {
            throw new BadRequestException("Sessiya yo'q");
        }
        return (Long) uid;
    }

    private Long requireAccountId(HttpServletRequest request) {
        Object aid = request.getAttribute(JwtAuthFilter.ATTR_ACCOUNT_ID);
        if (aid == null) {
            throw new BadRequestException("Sessiya yo'q");
        }
        return (Long) aid;
    }

    private static SubscriptionPlan parsePlan(String raw) {
        try {
            return SubscriptionPlan.valueOf(raw.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new BadRequestException("Noma'lum reja: " + raw);
        }
    }

    private static PaymentView toView(Payment p) {
        return new PaymentView(p.getId(), p.getPlan().name(), p.getAmountUzs(),
                p.getMonths(), p.getStatus().name(), p.getProvider(),
                p.getCreatedAt(), p.getPaidAt());
    }
}
