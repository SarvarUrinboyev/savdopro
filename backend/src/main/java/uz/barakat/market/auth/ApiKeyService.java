package uz.barakat.market.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.ApiKey;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.repository.ApiKeyRepository;

/**
 * Issues, hashes, lists and revokes external Open-API keys. The plaintext
 * secret ({@code sk_live_<random>}) is returned to the caller exactly once at
 * creation; only its SHA-256 hash is persisted.
 */
@Service
public class ApiKeyService {

    /** Prefix that marks a token as an API key (vs a JWT) for the auth filters. */
    public static final String TOKEN_PREFIX = "sk_live_";

    /** The scopes an API key may be granted (each maps to a SCOPE_ authority). */
    public static final Set<String> VALID_SCOPES = Set.of(
            "catalog:read", "sales:read", "customers:read", "orders:read", "accounting:read");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository repo;

    public ApiKeyService(ApiKeyRepository repo) {
        this.repo = repo;
    }

    /** Result of issuing a key — carries the one-time plaintext secret. */
    public record IssuedKey(Long id, String name, String scopes, String prefix, String secret) { }

    /**
     * Creates a key for the current shop (tenant tagged by {@code @PrePersist}).
     * Returns the plaintext once; thereafter only the prefix is recoverable.
     */
    @Transactional
    public IssuedKey create(String name, List<String> scopes, LocalDateTime expiresAt) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Kalit nomi kerak");
        }
        Set<String> requested = new LinkedHashSet<>();
        if (scopes != null) {
            for (String s : scopes) {
                if (s == null) continue;
                String norm = s.trim().toLowerCase();
                if (!norm.isEmpty()) requested.add(norm);
            }
        }
        if (requested.isEmpty()) {
            throw new BadRequestException("Kamida bitta ruxsat (scope) tanlang");
        }
        for (String s : requested) {
            if (!VALID_SCOPES.contains(s)) {
                throw new BadRequestException("Noma'lum ruxsat: " + s);
            }
        }
        String secret = TOKEN_PREFIX + randomToken();
        ApiKey key = new ApiKey();
        key.setName(name.strip());
        key.setKeyHash(hash(secret));
        key.setKeyPrefix(secret.substring(0, Math.min(16, secret.length())));
        key.setScopes(String.join(",", requested));
        key.setActive(true);
        key.setExpiresAt(expiresAt);
        ApiKey saved = repo.save(key);
        return new IssuedKey(saved.getId(), saved.getName(), saved.getScopes(),
                saved.getKeyPrefix(), secret);
    }

    @Transactional(readOnly = true)
    public List<ApiKey> list() {
        return repo.findByOrderByCreatedAtDesc();
    }

    /** Revokes (deactivates) a key. Tenant-guarded via {@code findById} @PostLoad. */
    @Transactional
    public void revoke(Long id) {
        ApiKey key = repo.findById(id)
                .orElseThrow(() -> new BadRequestException("Kalit topilmadi"));
        key.setActive(false);
        repo.save(key);
    }

    /** Resolves a presented secret to its key row, or empty if unknown. */
    @Transactional(readOnly = true)
    public Optional<ApiKey> resolve(String secret) {
        if (secret == null || !secret.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }
        return repo.findByKeyHash(hash(secret));
    }

    /** Best-effort last-used stamp; only writes when stale to avoid per-request writes. */
    @Transactional
    public void touchLastUsed(Long id) {
        repo.findById(id).ifPresent(k -> k.setLastUsedAt(LocalDateTime.now()));
    }

    public List<String> scopesOf(ApiKey key) {
        if (key.getScopes() == null || key.getScopes().isBlank()) {
            return List.of();
        }
        return Arrays.stream(key.getScopes().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- crypto

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex of the secret. */
    public static String hash(String secret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(secret.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
