package uz.barakat.market.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.market.domain.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findAllByOrderByNameAsc();

    /** True if any supplier in the active tenant already has this phone. */
    boolean existsByPhone(String phone);

    /** Same, excluding one id — used on edit so a row keeps its own number. */
    boolean existsByPhoneAndIdNot(String phone, Long id);
}
