package uz.barakat.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/**
 * A per-integration API key for the external Open API ({@code /api/v1/**}).
 *
 * <p>Only a SHA-256 hash of the secret is stored — the plaintext (prefixed
 * {@code sk_live_}) is shown to the owner exactly once at creation. The key is
 * shop-scoped: it grants read access to the data of {@link #getShopId()} only,
 * with the granted {@link #getScopes()} (CSV of {@code resource:read}). Auth
 * lookup ({@code findByKeyHash}) runs before any tenant scope is set, so the
 * row loads with an empty (trusted) scope; the owner's management list, run
 * under their JWT, is tenant-filtered as usual.
 */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Filter(name = "accountFilter", condition = "shop_id IN (:shopIds)")
@Entity
@Table(name = "api_keys")
@Getter
@Setter
public class ApiKey extends TenantScopedEntity {

    /** Human label for the integration, e.g. "Uzum marketplace". */
    @Column(nullable = false, length = 120)
    private String name;

    /** SHA-256 hex of the full secret. Globally unique; the secret is never stored. */
    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    /** Display-only prefix, e.g. {@code sk_live_a1b2c3} — lets the owner tell keys apart. */
    @Column(name = "key_prefix", nullable = false, length = 24)
    private String keyPrefix;

    /** CSV of granted scopes, e.g. {@code catalog:read,sales:read}. */
    @Column(nullable = false, length = 255)
    private String scopes;

    @Column(nullable = false)
    private boolean active = true;

    /** Optional hard expiry; null = never expires. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** Last time the key authenticated a request (throttled write). */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
}
