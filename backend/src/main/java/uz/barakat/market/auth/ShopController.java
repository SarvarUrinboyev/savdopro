package uz.barakat.market.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.auth.ShopService.CreateShopRequest;
import uz.barakat.market.auth.ShopService.ShopResponse;
import uz.barakat.market.auth.ShopService.UpdateShopRequest;
import uz.barakat.market.exception.BadRequestException;

/**
 * Shops scoped to the caller's account. Any logged-in user can list
 * their own account's shops; only the account owner (or super-admin)
 * can create / delete / switch the main shop.
 */
@RestController
@RequestMapping("/api/shops")
public class ShopController {

    private final ShopService service;

    public ShopController(ShopService service) {
        this.service = service;
    }

    @GetMapping
    public List<ShopResponse> list(HttpServletRequest request) {
        return service.list(currentAccountId(request));
    }

    @PostMapping
    public ShopResponse create(HttpServletRequest request,
                                @Valid @RequestBody CreateShopRequest body) {
        requireOwner(request);
        return service.create(currentAccountId(request), currentMaxShops(request), body);
    }

    @PutMapping("/{id}")
    public ShopResponse update(HttpServletRequest request,
                                @PathVariable Long id,
                                @Valid @RequestBody UpdateShopRequest body) {
        requireOwner(request);
        return service.update(currentAccountId(request), id, body);
    }

    @PatchMapping("/{id}/main")
    public ShopResponse setMain(HttpServletRequest request, @PathVariable Long id) {
        requireOwner(request);
        return service.setMain(currentAccountId(request), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        requireOwner(request);
        service.delete(currentAccountId(request), id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------ helpers

    private static Long currentAccountId(HttpServletRequest request) {
        Long id = (Long) request.getAttribute(JwtAuthFilter.ATTR_ACCOUNT_ID);
        if (id == null) {
            throw new BadRequestException("Sessiya yaroqsiz");
        }
        return id;
    }

    /** The plan's shop limit from the JWT; missing (legacy token) = unlimited. */
    private static int currentMaxShops(HttpServletRequest request) {
        Object ms = request.getAttribute(JwtAuthFilter.ATTR_MAX_SHOPS);
        return (ms instanceof Integer i) ? i : Integer.MAX_VALUE;
    }

    /** Mutations require ACCOUNT_OWNER or SUPER_ADMIN. */
    private static void requireOwner(HttpServletRequest request) {
        String role = (String) request.getAttribute(JwtAuthFilter.ATTR_ROLE);
        if (!"ACCOUNT_OWNER".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new BadRequestException("Faqat akkaunt egasi do'konlarni boshqara oladi");
        }
    }
}
