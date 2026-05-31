package uz.barakat.license.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.license.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** An account's payments, newest first — for the billing history view. */
    List<Payment> findByAccountIdOrderByCreatedAtDescIdDesc(Long accountId);

    /** Look a payment up by Payme's transaction id (Perform/Cancel/Check legs). */
    Optional<Payment> findByPaymeTxId(String paymeTxId);
}
