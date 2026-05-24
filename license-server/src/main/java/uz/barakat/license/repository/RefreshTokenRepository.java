package uz.barakat.license.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.license.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoke every still-valid refresh token for a user. Used by logout
     * (this device) and by account-block (all devices). Idempotent: rows
     * already revoked are left alone.
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now "
            + "WHERE r.userId = :userId AND r.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Same as above but scoped to an account. Fires when a super-admin
     * blocks an account — every device using any user of that account
     * loses its session on the next refresh attempt.
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now "
            + "WHERE r.accountId = :accountId AND r.revokedAt IS NULL")
    int revokeAllForAccount(@Param("accountId") Long accountId, @Param("now") LocalDateTime now);

    /** Housekeeping: drop expired rows. Called by a scheduled job. */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
