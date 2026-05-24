package uz.barakat.license.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.license.domain.RefreshToken;
import uz.barakat.license.exception.BadRequestException;
import uz.barakat.license.repository.RefreshTokenRepository;

/**
 * Refresh-token lifecycle.
 *
 * <p>On login: {@link #issue} generates a 32-byte random token, returns
 * the plaintext to the client, and persists only the SHA-256 hash.
 *
 * <p>On refresh: {@link #consume} re-hashes the incoming token, checks
 * it's known + unexpired + unrevoked, marks {@code lastUsedAt}, and
 * (security best practice) rotates — the old token is revoked and a
 * brand-new one is issued so a stolen token can be used at most once.
 *
 * <p>Cleanup: a daily job drops rows past their expiry to keep the
 * table size bounded.
 */
@Service
@Transactional
public class RefreshTokenService {

    /** 7 days — long enough for daily-use cashiers, short enough to limit blast radius of a leak. */
    public static final long REFRESH_TTL_DAYS = 7;

    private static final SecureRandom RNG = new SecureRandom();

    private final RefreshTokenRepository tokens;

    public RefreshTokenService(RefreshTokenRepository tokens) {
        this.tokens = tokens;
    }

    public record Issued(String plaintext, LocalDateTime expiresAt) { }

    /** Issue a brand-new refresh token tied to the user / account / client IP. */
    public Issued issue(Long userId, Long accountId, String clientIp) {
        String plaintext = generatePlaintext();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime exp = now.plusDays(REFRESH_TTL_DAYS);

        RefreshToken row = new RefreshToken();
        row.setTokenHash(sha256(plaintext));
        row.setUserId(userId);
        row.setAccountId(accountId);
        row.setIssuedAt(now);
        row.setExpiresAt(exp);
        row.setClientIp(clientIp);
        tokens.save(row);

        return new Issued(plaintext, exp);
    }

    /**
     * Validate an incoming plaintext token and rotate it. Returns the
     * persisted row so the caller can mint a fresh access token with
     * the right user / account claims, plus the new plaintext refresh
     * token to hand back to the client.
     *
     * <p>Throws {@link BadRequestException} if the token is unknown,
     * already revoked, or expired — the caller maps this to 401 so the
     * client knows to bounce the user to the login screen.
     */
    public RotationResult consume(String plaintext, String clientIp) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new BadRequestException("Refresh token kiritilmagan");
        }
        RefreshToken row = tokens.findByTokenHash(sha256(plaintext))
                .orElseThrow(() -> new BadRequestException("Refresh token noto'g'ri"));
        if (!row.isUsable()) {
            throw new BadRequestException("Refresh token muddati tugagan yoki bekor qilingan");
        }
        // Rotate: revoke the consumed token, mint a new one. If the
        // client retries the same plaintext (e.g. flaky network) the
        // second attempt will fail with "noto'g'ri" — that's intentional;
        // simultaneous reuse is the canary for a stolen token.
        LocalDateTime now = LocalDateTime.now();
        row.setRevokedAt(now);
        row.setLastUsedAt(now);
        tokens.save(row);

        Issued fresh = issue(row.getUserId(), row.getAccountId(), clientIp);
        return new RotationResult(row.getUserId(), row.getAccountId(), fresh);
    }

    public record RotationResult(Long userId, Long accountId, Issued fresh) { }

    /** Single-device logout: revoke just this token. Used on `POST /api/auth/logout`. */
    public void revoke(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return;
        tokens.findByTokenHash(sha256(plaintext)).ifPresent(row -> {
            if (row.getRevokedAt() == null) {
                row.setRevokedAt(LocalDateTime.now());
                tokens.save(row);
            }
        });
    }

    public int revokeAllForUser(Long userId) {
        return tokens.revokeAllForUser(userId, LocalDateTime.now());
    }

    public int revokeAllForAccount(Long accountId) {
        return tokens.revokeAllForAccount(accountId, LocalDateTime.now());
    }

    /**
     * Daily housekeeping — drop tokens that have been expired for at
     * least a day. Kept conservative so audit-style "when did this user
     * last log in" queries still find recent rows.
     */
    @Scheduled(cron = "0 30 3 * * *")
    public void purgeExpired() {
        tokens.deleteExpiredBefore(LocalDateTime.now().minusDays(1));
    }

    // ------------------------------------------------------------ helpers

    /**
     * 32 random bytes, URL-safe Base64 — 43-char string with ~256 bits
     * of entropy. Plenty against brute force; rotation makes guessing
     * irrelevant in practice.
     */
    private static String generatePlaintext() {
        byte[] buf = new byte[32];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JRE — this can never fire.
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /** Accessor used by tests / debug endpoints. */
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findActive(String plaintext) {
        return tokens.findByTokenHash(sha256(plaintext)).filter(RefreshToken::isUsable);
    }
}
