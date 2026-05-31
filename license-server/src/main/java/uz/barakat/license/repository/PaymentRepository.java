package uz.barakat.license.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.license.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** An account's payments, newest first — for the billing history view. */
    List<Payment> findByAccountIdOrderByCreatedAtDescIdDesc(Long accountId);
}
