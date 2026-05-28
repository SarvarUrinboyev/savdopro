package uz.barakat.license.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link LoginRateLimiter}. The Telegram side-effect is
 * mocked — we only assert <em>that</em> the alerter fires (or doesn't),
 * not what it sends over the wire.
 */
@ExtendWith(MockitoExtension.class)
class LoginRateLimiterTest {

    private static final String IP = "203.0.113.7";

    @Mock private SuspiciousLoginAlerter alerter;

    @Test
    void allowsLoginWhenBucketIsEmpty() {
        LoginRateLimiter limiter = new LoginRateLimiter(alerter);

        assertTrue(limiter.allow(IP), "fresh IP must be allowed");
        verify(alerter, never()).notifyLockout(any(), any());
    }

    @Test
    void doesNotAlertOnFailuresBelowThreshold() {
        LoginRateLimiter limiter = new LoginRateLimiter(alerter);

        // 9 failures — one short of the 10-attempt threshold.
        for (int i = 0; i < 9; i++) {
            limiter.recordFailure(IP);
        }

        assertTrue(limiter.allow(IP), "still under the threshold, must allow");
        verify(alerter, never()).notifyLockout(any(), any());
    }

    @Test
    void firesAlertExactlyOnceWhenLockoutTrips() {
        LoginRateLimiter limiter = new LoginRateLimiter(alerter);

        // 10 failures triggers the lockout.
        for (int i = 0; i < 10; i++) {
            limiter.recordFailure(IP);
        }

        assertFalse(limiter.allow(IP), "10 failures must lock the IP out");
        verify(alerter, times(1)).notifyLockout(eq(IP), any(Instant.class));

        // Further failures within the lockout window must NOT spam the channel.
        limiter.recordFailure(IP);
        limiter.recordFailure(IP);
        verify(alerter, times(1)).notifyLockout(eq(IP), any(Instant.class));
    }

    @Test
    void successResetsBucketAndPreservesAlertOnNextTrip() {
        LoginRateLimiter limiter = new LoginRateLimiter(alerter);

        for (int i = 0; i < 9; i++) {
            limiter.recordFailure(IP);
        }
        limiter.recordSuccess(IP);
        // Bucket was wiped; the alerter has never been called.
        verify(alerter, never()).notifyLockout(any(), any());

        // A fresh attack from the same IP must still be able to trigger one alert.
        for (int i = 0; i < 10; i++) {
            limiter.recordFailure(IP);
        }
        verify(alerter, times(1)).notifyLockout(eq(IP), any(Instant.class));
    }

    @Test
    void blankIpIsTreatedAsAllowedAndNeverAlerts() {
        LoginRateLimiter limiter = new LoginRateLimiter(alerter);

        assertTrue(limiter.allow(""));
        assertTrue(limiter.allow(null));
        limiter.recordFailure("");
        limiter.recordFailure(null);

        verify(alerter, never()).notifyLockout(any(), any());
    }
}
