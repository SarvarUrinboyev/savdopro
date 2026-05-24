package uz.barakat.market.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.market.domain.ShopTransfer;

public interface ShopTransferRepository extends JpaRepository<ShopTransfer, Long> {

    /** Most recent first; used by the transfers list page. */
    List<ShopTransfer> findByAccountIdOrderByCreatedAtDesc(Long accountId);
}
