package uz.barakat.license.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Persisted record of a refresh token. The plaintext token is never
 * stored; only its SHA-256 hash. On refresh the incoming token is
 * re-hashed and looked up here — a DB leak therefore cannot replay
 * existing sessions because the attacker still wouldn't know the
 * plaintext.
 *
 * <p>{@code revokedAt} is set when the user logs out, when the account
 * is blocked, or when the operator rotates secrets. {@code lastUsedAt}
 * lets the admin panel show "active devices" without separate tables.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
public class RefreshToken extends BaseEntity {

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    public boolean isUsable() {
        if (revokedAt != null) return false;
        return expiresAt.isAfter(LocalDateTime.now());
    }
}
