package uz.barakat.license;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Always run {@code flyway.repair()} before {@code migrate()}.
 *
 * <p>Dev-only convenience: when a migration's contents change between
 * build cycles (we tweak a column type, fix a typo, etc.) Flyway would
 * normally refuse to start because the checksum stored in
 * {@code flyway_schema_history} no longer matches the file on disk.
 * Calling {@code repair()} first re-aligns the checksums so the
 * next {@code migrate()} can proceed.
 *
 * <p>In production this is still safe because {@code migrate()} would
 * be a no-op if the schema is already up to date — repair only touches
 * the history table, never the user data.
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
