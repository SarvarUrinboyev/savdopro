package uz.barakat.license;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the full license-server context on an in-memory H2 so every bean
 * wires up and the whole Flyway chain (V1..V10) applies, then Hibernate
 * validates the entities against the migrated schema — including the new
 * {@code accounts.plan} column. The cheapest guard against migration /
 * entity drift and security/JPA wiring regressions.
 */
@SpringBootTest
@ActiveProfiles("test")
class LicenseContextSmokeTest {

    @Test
    void contextLoads() {
        // Success = Flyway migrated a fresh DB and ddl-auto=validate passed.
    }
}
