package uz.barakat.market.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.market.domain.WebhookSubscription;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, Long> {

    /** Active subscriptions for the current shop (tenant-filtered) — used at enqueue. */
    List<WebhookSubscription> findByActiveTrue();

    /** Management list for the current shop, newest first. */
    List<WebhookSubscription> findByOrderByCreatedAtDesc();
}
