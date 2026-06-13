package uz.barakat.market.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.market.domain.OnlinePayment;

/**
 * Online payment records. Queried by the provider webhooks, which run with
 * no tenant context, so lookups are by absolute id / provider transaction id.
 */
public interface OnlinePaymentRepository extends JpaRepository<OnlinePayment, Long> {

    Optional<OnlinePayment> findByProviderAndProviderTxnId(String provider, String providerTxnId);

    Optional<OnlinePayment> findFirstByProviderAndCustomerIdAndStateOrderByIdDesc(
            String provider, Long customerId, int state);

    /** A shop's online payments (newest first) — for reconciliation. */
    List<OnlinePayment> findByShopIdOrderByIdDesc(Long shopId);
}
