package uz.barakat.market.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.market.domain.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findAllByOrderByNameAsc();

    /** Finds the customer linked to a Telegram chat (self-service bot). */
    Optional<Customer> findByTelegramChatId(Long telegramChatId);

    /** Finds the customer whose loyalty card QR matches the scanned code. */
    Optional<Customer> findByCardCode(String cardCode);

    /** True if any customer in the active tenant already has this phone. */
    boolean existsByPhone(String phone);

    /** Same, excluding one id — used on edit so a row keeps its own number. */
    boolean existsByPhoneAndIdNot(String phone, Long id);
}
