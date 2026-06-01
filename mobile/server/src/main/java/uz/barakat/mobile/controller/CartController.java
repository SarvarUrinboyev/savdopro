package uz.barakat.mobile.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.mobile.config.CurrentUser;
import uz.barakat.mobile.dto.CartDtos.CartResponse;
import uz.barakat.mobile.dto.CartDtos.SetQuantityRequest;
import uz.barakat.mobile.service.CartService;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse get() {
        return cartService.get(CurrentUser.id());
    }

    @PutMapping("/items/{productId}")
    public CartResponse setQuantity(@PathVariable Long productId,
                                    @Valid @RequestBody SetQuantityRequest req) {
        return cartService.setQuantity(CurrentUser.id(), productId, req.quantity());
    }

    @DeleteMapping
    public CartResponse clear() {
        return cartService.clear(CurrentUser.id());
    }
}
