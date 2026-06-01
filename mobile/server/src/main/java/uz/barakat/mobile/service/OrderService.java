package uz.barakat.mobile.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.barakat.mobile.domain.Address;
import uz.barakat.mobile.domain.Cart;
import uz.barakat.mobile.domain.DeliveryType;
import uz.barakat.mobile.domain.Order;
import uz.barakat.mobile.domain.OrderItem;
import uz.barakat.mobile.dto.OrderDtos.CreateOrderRequest;
import uz.barakat.mobile.dto.OrderDtos.OrderResponse;
import uz.barakat.mobile.repository.AddressRepository;
import uz.barakat.mobile.repository.OrderRepository;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final AddressRepository addresses;
    private final CartService cartService;

    @Value("${app.delivery.fee}") private long deliveryFee;
    @Value("${app.delivery.free-threshold}") private long freeThreshold;

    public OrderService(OrderRepository orders, AddressRepository addresses, CartService cartService) {
        this.orders = orders;
        this.addresses = addresses;
        this.cartService = cartService;
    }

    @Transactional
    public OrderResponse create(Long customerId, CreateOrderRequest req) {
        Cart cart = cartService.getOrCreateCart(customerId);
        cartService.removeSoldOut(cart);
        if (cart.getItems().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Savat bo'sh");
        }

        Order order = new Order();
        order.setCustomer(cart.getCustomer());
        order.setDeliveryType(req.deliveryType());
        order.setPaymentMethod(req.paymentMethod());
        order.setDeliverySlot(req.deliverySlot());
        order.setComment(req.comment());

        if (req.deliveryType() == DeliveryType.DELIVERY) {
            if (req.addressId() == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Yetkazib berish uchun manzil kerak");
            }
            Address addr = addresses.findByIdAndCustomerId(req.addressId(), customerId)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Manzil topilmadi"));
            order.setAddressLine(addr.getAddressLine());
        }

        long itemsTotal = 0;
        for (var ci : cart.getItems()) {
            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setProduct(ci.getProduct());
            oi.setProductName(ci.getProduct().getName());
            oi.setUnitPrice(ci.getProduct().getPrice());
            oi.setQuantity(ci.getQuantity());
            order.getItems().add(oi);
            itemsTotal += oi.getLineTotal();
        }

        long fee = computeDeliveryFee(req.deliveryType(), itemsTotal);
        order.setItemsTotal(itemsTotal);
        order.setDeliveryFee(fee);
        order.setTotal(itemsTotal + fee);

        Order saved = orders.save(order);
        cartService.clear(customerId);
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(Long customerId) {
        return orders.findByCustomerIdOrderByIdDesc(customerId).stream()
                .map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long customerId, Long orderId) {
        Order order = orders.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Buyurtma topilmadi"));
        return OrderResponse.from(order);
    }

    private long computeDeliveryFee(DeliveryType type, long itemsTotal) {
        if (type == DeliveryType.PICKUP) return 0;
        if (freeThreshold > 0 && itemsTotal >= freeThreshold) return 0;
        return deliveryFee;
    }
}
