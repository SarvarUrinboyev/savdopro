package uz.barakat.market.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.market.domain.PurchaseLot;

public interface PurchaseLotRepository extends JpaRepository<PurchaseLot, Long> {

    /** A product's receipts oldest-first — the FIFO layer order + price history. */
    List<PurchaseLot> findByProductIdOrderByReceiptDateAscIdAsc(Long productId);

    /** All receipts newest-first — the global purchase-price history feed. */
    List<PurchaseLot> findAllByOrderByReceiptDateDescIdDesc();
}
