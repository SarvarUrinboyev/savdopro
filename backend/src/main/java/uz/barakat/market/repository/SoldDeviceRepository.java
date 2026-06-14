package uz.barakat.market.repository;

import java.util.List;
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
}
