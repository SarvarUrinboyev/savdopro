package uz.barakat.mobile.dto;

import jakarta.validation.constraints.NotNull;
import uz.barakat.mobile.domain.DeliveryType;
import uz.barakat.mobile.domain.Order;
import uz.barakat.mobile.domain.OrderItem;
import uz.barakat.mobile.domain.PaymentMethod;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Buyurtma javob/so'rov modellari. */
public final class OrderDtos {

    private OrderDtos() {}

    public record CreateOrderRequest(
            Long addressId,
            @NotNull DeliveryType deliveryType,
            @NotNull PaymentMethod paymentMethod,
            LocalDateTime deliverySlot,
            String comment
    ) {}

    public record OrderItemResponse(Long productId, String productName, long unitPrice,
                                    int quantity, long lineTotal) {
        public static OrderItemResponse from(OrderItem i) {
            return new OrderItemResponse(
                    i.getProduct() != null ? i.getProduct().getId() : null,
                    i.getProductName(), i.getUnitPrice(), i.getQuantity(), i.getLineTotal());
        }
    }

    public record OrderResponse(
            Long id, String status, String deliveryType, String paymentMethod,
            String addressLine, LocalDateTime deliverySlot, String comment,
            long itemsTotal, long deliveryFee, long total,
            Instant createdAt, List<OrderItemResponse> items
    ) {
        public static OrderResponse from(Order o) {
            return new OrderResponse(
                    o.getId(), o.getStatus().name(), o.getDeliveryType().name(),
                    o.getPaymentMethod().name(), o.getAddressLine(), o.getDeliverySlot(),
                    o.getComment(), o.getItemsTotal(), o.getDeliveryFee(), o.getTotal(),
                    o.getCreatedAt(),
                    o.getItems().stream().map(OrderItemResponse::from).toList());
        }
    }
}
