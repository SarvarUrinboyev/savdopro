package uz.barakat.mobile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.mobile.domain.Address;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByCustomerIdOrderByIdDesc(Long customerId);
    Optional<Address> findByIdAndCustomerId(Long id, Long customerId);
}
