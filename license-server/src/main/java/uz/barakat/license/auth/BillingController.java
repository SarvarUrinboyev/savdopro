package uz.barakat.license.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.license.auth.AuthDtos.SubscriptionStatusResponse;
import uz.barakat.license.exception.BadRequestException;

/**
 * In-app billing: the current account's subscription snapshot — plan, trial
 * countdown, and user-limit usage — for the merchant's billing page.
 * Authenticated (the catch-all {@code /api/**} rule in SecurityConfig gates
 * it); the account is taken from the JWT, never from the request body.
 */
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final AuthService service;

    public BillingController(AuthService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public SubscriptionStatusResponse status(HttpServletRequest request) {
        Object uid = request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        if (uid == null) {
            throw new BadRequestException("Sessiya yo'q");
        }
        return service.subscriptionStatus((Long) uid);
    }
}
