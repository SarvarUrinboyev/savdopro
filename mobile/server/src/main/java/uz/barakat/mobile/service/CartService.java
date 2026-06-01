package uz.barakat.mobile.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.barakat.mobile.domain.Cart;
import uz.barakat.mobile.domain.CartItem;
import uz.barakat.mobile.domain.Customer;
import uz.barakat.mobile.domain.Product;
import uz.barakat.mobile.dto.CartDtos.CartResponse;
import uz.barakat.mobile.repository.CartRepository;
import uz.barakat.mobile.repository.CustomerRepository;
import uz.barakat.mobile.repository.ProductRepository;

import java.util.Iterator;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class CartService {

    private final CartRepository carts;
    private final CustomerRepository customers;
    private final ProductRepository products;

    @Value("${app.delivery.fee}") private long deliveryFee;

    public CartService(CartRepository carts, CustomerRepository customers, ProductRepository products) {
        this.carts = carts;
        this.customers = customers;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public CartResponse get(Long customerId) {
        return CartResponse.from(getOrCreateCart(customerId), deliveryFee);
    }

    @Transactional
    public CartResponse setQuantity(Long customerId, Long productId, int quantity) {
        if (quantity < 0) throw new ResponseStatusException(BAD_REQUEST, "Miqdor manfiy bo'lishi mumkin emas");
        Cart cart = getOrCreateCart(customerId);

        CartItem existing = cart.getItems().stream()
                .filter(i -> i.getProduct().getId().equals(productId))
                .findFirst().orElse(null);

        if (quantity == 0) {
            if (existing != null) {
                cart.getItems().remove(existing);
            }
        } else if (existing != null) {
            existing.setQuantity(quantity);
        } else {
            Product product = products.findById(productId)
                    .filter(Product::isInStock)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Mahsulot mavjud emas"));
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(quantity);
            cart.getItems().add(item);
        }
        carts.save(cart);
        return CartResponse.from(cart, deliveryFee);
    }

    @Transactional
    public CartResponse clear(Long customerId) {
        Cart cart = getOrCreateCart(customerId);
        cart.getItems().clear();
        carts.save(cart);
        return CartResponse.from(cart, deliveryFee);
    }

    /** Buyurtma yaratilgach savatni bo'shatish uchun (OrderService ishlatadi). */
    Cart getOrCreateCart(Long customerId) {
        return carts.findByCustomerId(customerId).orElseGet(() -> {
            Customer customer = customers.findById(customerId)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Mijoz topilmadi"));
            Cart cart = new Cart();
            cart.setCustomer(customer);
            return carts.save(cart);
        });
    }

    void removeSoldOut(Cart cart) {
        Iterator<CartItem> it = cart.getItems().iterator();
        while (it.hasNext()) {
            if (!it.next().getProduct().isInStock()) it.remove();
        }
    }
}
