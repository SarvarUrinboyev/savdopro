package uz.barakat.market.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uz.barakat.market.domain.Shop;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    /** Every shop id (filter-free) — used by background jobs to set a global
     *  tenant scope so native-query reports/forecasts aren't empty off-request. */
    @Query("SELECT s.id FROM Shop s")
    List<Long> findAllIds();

    List<Shop> findByAccountIdOrderByMainDescNameAsc(Long accountId);

    Optional<Shop> findFirstByAccountIdAndMainTrue(Long accountId);

    long countByAccountId(Long accountId);

    /** True when shop {@code id} exists AND belongs to {@code accountId}. */
    boolean existsByIdAndAccountId(Long id, Long accountId);

    /** Phone-uniqueness guards within one account's shops. */
    boolean existsByContactPhoneAndAccountId(String contactPhone, Long accountId);

    boolean existsByContactPhoneAndAccountIdAndIdNot(String contactPhone, Long accountId, Long id);
}
