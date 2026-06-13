package uz.barakat.market.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.market.domain.WebhookDelivery;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    /** Due work for the dispatcher (runs under GlobalScope → all shops). */
    List<WebhookDelivery> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            String status, LocalDateTime now, Pageable page);

    /** Recent deliveries for the current shop (tenant-filtered) — management view. */
    List<WebhookDelivery> findByOrderByCreatedAtDescIdDesc(Pageable page);
}
