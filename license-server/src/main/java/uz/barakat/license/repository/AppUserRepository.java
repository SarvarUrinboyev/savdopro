package uz.barakat.license.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uz.barakat.license.domain.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    List<AppUser> findByAccountIdOrderByUsernameAsc(Long accountId);

    boolean existsByUsernameIgnoreCase(String username);

    /** Telegram OAuth login looks the user up by their linked Telegram numeric id. */
    Optional<AppUser> findByTelegramId(Long telegramId);

    /**
     * Per-account user counts in a single grouped query. Used by the
     * admin account-list endpoint to avoid the N+1 fan-out of calling
     * {@code findByAccountIdOrderByUsernameAsc(id).size()} per row.
     */
    @Query("SELECT u.accountId AS accountId, COUNT(u) AS userCount "
            + "FROM AppUser u GROUP BY u.accountId")
    List<UserCountRow> countByAccountIdGrouped();

    interface UserCountRow {
        Long getAccountId();
        Long getUserCount();
    }

    /** Convenience: materialise the projection into a Map for O(1) lookup. */
    default Map<Long, Long> countsByAccountId() {
        return countByAccountIdGrouped().stream()
                .collect(Collectors.toMap(UserCountRow::getAccountId, UserCountRow::getUserCount));
    }
}
