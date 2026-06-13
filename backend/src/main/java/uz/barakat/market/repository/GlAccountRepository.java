package uz.barakat.market.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.market.domain.GlAccount;

public interface GlAccountRepository extends JpaRepository<GlAccount, Long> {

    List<GlAccount> findAllByOrderByCodeAsc();

    Optional<GlAccount> findFirstByCode(String code);

    boolean existsByCode(String code);

    /** Used to decide whether the standard chart still needs seeding for a shop. */
    long countBySystemTrue();
}
