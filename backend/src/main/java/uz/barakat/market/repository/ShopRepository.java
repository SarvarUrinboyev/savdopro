package uz.barakat.market.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.market.domain.Shop;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    List<Shop> findByAccountIdOrderByMainDescNameAsc(Long accountId);

    Optional<Shop> findFirstByAccountIdAndMainTrue(Long accountId);

    long countByAccountId(Long accountId);

    /** True when shop {@code id} exists AND belongs to {@code accountId}. */
    boolean existsByIdAndAccountId(Long id, Long accountId);
}
