package uz.barakat.license.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.license.domain.Account;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /** Account contact-phone uniqueness (global — one business per number). */
    boolean existsByContactPhone(String contactPhone);

    boolean existsByContactPhoneAndIdNot(String contactPhone, Long id);
}
