package uz.barakat.mobile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.mobile.domain.Customer;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByPhone(String phone);
    boolean existsByPhone(String phone);
}
