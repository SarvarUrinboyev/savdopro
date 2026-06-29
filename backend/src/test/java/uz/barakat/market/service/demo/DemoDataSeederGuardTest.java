package uz.barakat.market.service.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Pure unit test of the production-safety gate. The seed must be OFF unless
 * explicitly enabled, and must NEVER run under the prod or test profile even
 * when the flag is on. No Spring context — just the {@code enabled()} logic.
 */
class DemoDataSeederGuardTest {

    private static DemoDataSeeder seeder(boolean prop, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        // Only env + the flag participate in enabled(); the rest are unused here.
        return new DemoDataSeeder(env, null, prop, null, null, null, null, null, null, null, null);
    }

    @Test
    void offByDefault() {
        assertThat(seeder(false).enabled()).isFalse();
    }

    @Test
    void onWithExplicitFlag() {
        assertThat(seeder(true).enabled()).isTrue();
    }

    @Test
    void onUnderDevOrStagingProfile() {
        assertThat(seeder(false, "dev").enabled()).isTrue();
        assertThat(seeder(false, "staging").enabled()).isTrue();
    }

    @Test
    void hardDisabledUnderProdEvenWithFlag() {
        assertThat(seeder(true, "prod").enabled()).isFalse();
    }

    @Test
    void hardDisabledUnderTestEvenWithFlag() {
        assertThat(seeder(true, "test").enabled()).isFalse();
    }
}
