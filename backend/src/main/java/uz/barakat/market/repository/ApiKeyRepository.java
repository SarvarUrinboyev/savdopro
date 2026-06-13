package uz.barakat.market.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.market.domain.ApiKey;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    /** Auth lookup by SHA-256 hash. Runs before any tenant scope is set, so it
     *  resolves globally (the hash is unique); the row loads under an empty,
     *  trusted scope. */
    Optional<ApiKey> findByKeyHash(String keyHash);

    /** Management list for the current shop (tenant-filtered), newest first. */
    List<ApiKey> findByOrderByCreatedAtDesc();
}
