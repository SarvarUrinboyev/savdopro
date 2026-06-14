package uz.barakat.market.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.market.domain.SoldDevice;

/**
 * Sold-device (IMEI) records. All queries are shop-scoped by Hibernate's
 * {@code tenantFilter} (enabled per request), so they only ever see the
 * caller's own devices.
 */
public interface SoldDeviceRepository extends JpaRepository<SoldDevice, Long> {

    List<SoldDevice> findAllByOrderByCreatedAtDesc();

    List<SoldDevice> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    /** Find an in-stock unit by its primary IMEI, to flip it to SOLD at checkout. */
    Optional<SoldDevice> findFirstByImei1AndStatus(String imei1, String status);
}
