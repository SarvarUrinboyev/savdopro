package uz.barakat.license.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding-window rate limiter for the {@code /api/auth/login}
 * endpoint. Designed for the single-node License Server VPS deploy —
 * if we ever scale horizontally this should move to Redis or a managed
 * rate-limit service.
 *
 * <p>Policy: {@value #MAX_ATTEMPTS} failed attempts per source IP per
 * {@value #WINDOW_MINUTES} minutes triggers a {@value #LOCKOUT_MINUTES}
 * minute lockout. Successful login resets the counter for that IP. This
 * is good-enough brute-force protection — it slows an attacker from
 * thousands of guesses per second to a few per minute, which combined
 * with min-4-char passwords still leaves a determined attacker room but
 * stops casual scripted attacks dead.
 *
 * <p>Tracks state per IP only — no per-username key, because that would
 * let an attacker DoS a known user by spamming bad passwords for their
 * login. Tradeoff: behind a shared NAT, one bad user can rate-limit
 * the whole office. Acceptable for retail SaaS where each shop has its
 * own internet connection.
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MINUTES = 5;
    private static final long LOCKOUT_MINUTES = 15;

    /** Per-IP attempt counter; auto-evicts on success or window expiry. */
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private final SuspiciousLoginAlerter alerter;

    public LoginRateLimiter(SuspiciousLoginAlerter alerter) {
        this.alerter = alerter;
    }

    /**
     * Returns true if the caller may attempt to log in right now. Side-effect:
     * if this is a new IP or its window has rolled over, the bucket is
     * initialised so the next {@link #recordFailure(String)} starts from 0.
     */
    public boolean allow(String ip) {
        if (ip == null || ip.isBlank()) return true;
        Instant now = Instant.now();
        Bucket b = buckets.get(ip);
        if (b == null) return true;
        if (b.lockedUntil != null && now.isBefore(b.lockedUntil)) {
            return false;
        }
        if (b.lockedUntil != null && !now.isBefore(b.lockedUntil)) {
            // Lockout expired — wipe the bucket so a fresh window starts.
            buckets.remove(ip);
            return true;
        }
        if (b.windowStart != null
                && Duration.between(b.windowStart, now).toMinutes() >= WINDOW_MINUTES) {
            buckets.remove(ip);
            return true;
        }
        return b.failureCount.get() < MAX_ATTEMPTS;
    }

    /** Increment the failure counter; arm a lockout once the threshold trips. */
    public void recordFailure(String ip) {
        if (ip == null || ip.isBlank()) return;
        Instant now = Instant.now();
        Bucket b = buckets.computeIfAbsent(ip, k -> new Bucket(now));
        int count = b.failureCount.incrementAndGet();
        if (count >= MAX_ATTEMPTS && b.lockedUntil == null) {
            b.lockedUntil = now.plus(Duration.ofMinutes(LOCKOUT_MINUTES));
            log.warn("Login rate-limit tripped: ip={} failures={} locked until {}",
                    ip, count, b.lockedUntil);
            // Fire-and-forget: the alerter no-ops if Telegram isn't
            // configured, so this is safe to call unconditionally.
            alerter.notifyLockout(ip, b.lockedUntil);
        }
    }

    /** Successful login clears the bucket so the user starts fresh. */
    public void recordSuccess(String ip) {
        if (ip == null || ip.isBlank()) return;
        buckets.remove(ip);
    }

    /** Mutable per-IP state. Synchronised via the ConcurrentHashMap semantics. */
    private static final class Bucket {
        final Instant windowStart;
        final AtomicInteger failureCount = new AtomicInteger(0);
        volatile Instant lockedUntil;

        Bucket(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}
