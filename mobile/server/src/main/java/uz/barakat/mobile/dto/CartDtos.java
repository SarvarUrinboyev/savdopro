package uz.barakat.mobile.dto;

import jakarta.validation.constraints.Min;
import uz.barakat.mobile.domain.Cart;
import uz.barakat.mobile.domain.CartItem;

import java.util.List;

/** Savatcha javob/so'rov modellari. */
public final class CartDtos {

    private CartDtos() {}

    public record CartItemResponse(Long productId, String name, long price, int quantity,
                                   long lineTotal, String imageUrl) {
        public static CartItemResponse from(CartItem i) {
            return new CartItemResponse(
                    i.getProduct().getId(), i.getProduct().getName(), i.getProduct().getPrice(),
                    i.getQuantity(), i.getLineTotal(), i.getProduct().getImageUrl());
        }
    }

    public record CartResponse(List<CartItemResponse> items, int itemCount,
                               long subtotal, long deliveryFee, long total) {
        public static CartResponse from(Cart cart, long deliveryFee) {
            long subtotal = cart.getSubtotal();
            long fee = subtotal == 0 ? 0 : deliveryFee;
            return new CartResponse(
                    cart.getItems().stream().map(CartItemResponse::from).toList(),
                    cart.getItemCount(), subtotal, fee, subtotal + fee);
        }
    }

    public record SetQuantityRequest(@Min(0) int quantity) {}
}
