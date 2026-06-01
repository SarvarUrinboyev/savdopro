package uz.barakat.mobile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.mobile.domain.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerIdOrderByIdDesc(Long customerId);
    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);
}
