package uz.barakat.market.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Audit hardening: production CORS must fail closed. The combination of a
 * wildcard origin and {@code allowCredentials(true)} would let any website make
 * authenticated cross-origin calls, so it must never be reachable — not via a
 * wildcard value, and not via an empty prod config silently degrading to one.
 */
class WebConfigCorsPolicyTest {

    private static MockEnvironment env(String... profiles) {
        MockEnvironment e = new MockEnvironment();
        if (profiles.length > 0) {
            e.setActiveProfiles(profiles);
        }
        return e;
    }

    @Test
    void wildcardWithCredentialsRefusesToStart() {
        assertThatThrownBy(() -> new WebConfig(new String[]{"*"}, null, env("prod")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wildcardRejectedEvenInDev() {
        assertThatThrownBy(() ->
                new WebConfig(new String[]{"https://shop.example.com", "*"}, null, env()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prodWithNoOriginsRefusesToStart() {
        // WEB_ALLOWED_ORIGINS unset → property resolves to an empty value.
        assertThatThrownBy(() -> new WebConfig(new String[]{""}, null, env("prod")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WebConfig(new String[]{}, null, env("prod")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prodWithExplicitOriginStarts() {
        assertThatCode(() ->
                new WebConfig(new String[]{"https://167-172-164-214.nip.io"}, null, env("prod")))
                .doesNotThrowAnyException();
    }

    @Test
    void devWithEmptyOriginsStartsWithoutWildcard() {
        // Non-prod convenience: an empty list warns but doesn't crash the
        // desktop build — and crucially never becomes "*".
        assertThatCode(() -> new WebConfig(new String[]{}, null, env()))
                .doesNotThrowAnyException();
    }
}
