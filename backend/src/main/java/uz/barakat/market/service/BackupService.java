package uz.barakat.market.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.barakat.market.telegram.TelegramService;

/**
 * Daily snapshot of the H2 data file. Runs at 03:00 server time and on
 * startup if the latest backup is older than {@code backup.fresh-hours}.
 *
 * <p>What it does:
 *   <ol>
 *     <li>Copy {@code barakat.mv.db} → {@code backups/barakat-YYYY-MM-DD-HHmm.mv.db}</li>
 *     <li>Prune backups older than {@code backup.retain-days} (default 30)</li>
 *     <li>Telegram notify with file size + count of retained backups</li>
 *   </ol>
 *
 * <p>Why a copy (not a JDBC dump): H2 in MVStore mode is crash-safe and
 * lets us safely copy the file while the JVM holds the write lock — the
 * MVStore page that's mid-write is journaled, so the copied file always
 * recovers to a consistent state on next open. Cheap and correct.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm");

    private final TelegramService telegram;
    private final DataSource dataSource;
    private final Path dbFile;
    private final Path backupDir;
    private final int retainDays;
    private final int freshHours;
    /** True on PostgreSQL — then this service MONITORS the cron pg_dump instead
     *  of running H2's BACKUP TO (which Postgres rejects with a syntax error). */
    private final boolean isPostgres;

    public BackupService(
            TelegramService telegram,
            DataSource dataSource,
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${backup.dir:./backups}") String backupDir,
            @Value("${backup.retain-days:30}") int retainDays,
            @Value("${backup.fresh-hours:24}") int freshHours) {
        this.telegram = telegram;
        this.dataSource = dataSource;
        this.dbFile = resolveDbFile(jdbcUrl);
        this.backupDir = Paths.get(backupDir).toAbsolutePath();
        this.retainDays = retainDays;
        this.freshHours = freshHours;
        this.isPostgres = jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql");
    }

    @PostConstruct
    public void onStartup() {
        try {
            Files.createDirectories(backupDir);
        } catch (IOException ex) {
            log.warn("Could not create backup dir {}: {}", backupDir, ex.getMessage());
            return;
        }
        if (needsFreshBackup()) {
            log.info("Last backup older than {}h — running backup now", freshHours);
            runBackup("startup");
        } else {
            log.info("Last backup is fresh, skipping startup backup");
        }
    }

    /** Daily snapshot at 03:00 server time. Cron: sec min hour day month wday */
    @Scheduled(cron = "${backup.cron:0 0 3 * * *}")
    public void daily() {
        runBackup("scheduled");
    }

    public synchronized void runBackup(String trigger) {
        if (isPostgres) {
            monitorCronBackup(trigger);
            return;
        }
        String stamp = STAMP.format(LocalDateTime.now());
        // H2's BACKUP TO writes a ZIP that includes the consistent
        // .mv.db snapshot. Safe to run while the DB is open — unlike
        // Files.copy() which loses on Windows file locks.
        Path out = backupDir.resolve("barakat-" + stamp + ".zip");
        try {
            Instant t0 = Instant.now();
            try (Connection conn = dataSource.getConnection();
                 Statement st = conn.createStatement()) {
                // BACKUP TO requires an absolute path quoted as a SQL literal.
                String sqlPath = out.toString().replace("\\", "/");
                st.execute("BACKUP TO '" + sqlPath + "'");
            }
            long bytes = Files.size(out);
            long ms = Duration.between(t0, Instant.now()).toMillis();
            int removed = pruneOld();
            int total = (int) listBackups().count();
            String size = humanBytes(bytes);
            log.info("Backup ok [{}]: {} ({}, {} ms). Pruned {}, total kept {}.",
                    trigger, out.getFileName(), size, ms, removed, total);
            try {
                telegram.sendMessage(String.format(
                        "[*] Backup tayyor (%s)%n"
                        + "Fayl: %s%n"
                        + "Hajm: %s%n"
                        + "Saqlangan: %d ta (oxirgi %d kun)%n"
                        + "O'chirildi: %d ta eski",
                        trigger, out.getFileName(), size, total, retainDays, removed));
            } catch (Exception ignore) { /* Telegram down — non-fatal */ }
        } catch (SQLException | IOException ex) {
            log.error("Backup FAILED [{}] -> {}: {}", trigger, out, ex.getMessage(), ex);
            try {
                telegram.sendMessage("[!] Backup XATOLIK: " + ex.getMessage());
            } catch (Exception ignore) { /* */ }
        }
    }

    // ------------------------------------------------------------ helpers

    private boolean needsFreshBackup() {
        return (isPostgres ? listPgBackups() : listBackups())
                .map(BackupService::lastModified)
                .max(Comparator.naturalOrder())
                .map(t -> Duration.between(t, Instant.now()).toHours() >= freshHours)
                .orElse(true);
    }

    /**
     * On PostgreSQL the real backup is the system cron's {@code pg_dump}
     * (pg-*.sql.gz in the backups dir). We don't duplicate it — we VERIFY it is
     * fresh and report to the owner, so a silently-failing cron is caught, and
     * the broken H2 {@code BACKUP TO} (rejected by Postgres) is never run.
     */
    private void monitorCronBackup(String trigger) {
        try {
            List<Path> pg = listPgBackups()
                    .sorted(Comparator.comparing(BackupService::lastModified).reversed())
                    .toList();
            if (pg.isEmpty()) {
                telegram.sendMessage("[!] Backup OGOHLANTIRISH: zaxira fayli topilmadi ("
                        + backupDir + "). Cron pg_dump'ni tekshiring!");
                log.warn("Backup monitor [{}]: no pg_dump backup in {}", trigger, backupDir);
                return;
            }
            Path latest = pg.get(0);
            long ageHours = Duration.between(lastModified(latest), Instant.now()).toHours();
            String size = humanBytes(Files.size(latest));
            if (ageHours >= freshHours) {
                telegram.sendMessage(String.format(
                        "[!] Backup OGOHLANTIRISH [%s]: oxirgi zaxira %d soat oldin (%s). "
                        + "Cron pg_dump'ni tekshiring!", trigger, ageHours, latest.getFileName()));
                log.warn("Backup monitor [{}]: latest pg_dump {}h old ({})",
                        trigger, ageHours, latest.getFileName());
            } else {
                telegram.sendMessage(String.format(
                        "[*] Backup OK (pg_dump) [%s]%nFayl: %s%nHajm: %s%nSaqlangan: %d ta",
                        trigger, latest.getFileName(), size, pg.size()));
                log.info("Backup monitor [{}]: {} ({}, {}h old), {} kept",
                        trigger, latest.getFileName(), size, ageHours, pg.size());
            }
        } catch (Exception ex) {
            log.warn("Backup monitor failed [{}]: {}", trigger, ex.toString());
        }
    }

    private Stream<Path> listPgBackups() {
        try {
            return Files.list(backupDir).filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("pg-") && n.endsWith(".sql.gz");
            });
        } catch (IOException ex) {
            return Stream.empty();
        }
    }

    private Stream<Path> listBackups() {
        try {
            return Files.list(backupDir)
                    .filter(p -> p.getFileName().toString().startsWith("barakat-"))
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".zip") || n.endsWith(".mv.db");
                    });
        } catch (IOException ex) {
            return Stream.empty();
        }
    }

    private int pruneOld() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retainDays));
        List<Path> old = listBackups()
                .filter(p -> lastModified(p).isBefore(cutoff))
                .toList();
        int n = 0;
        for (Path p : old) {
            try {
                Files.deleteIfExists(p);
                n++;
            } catch (IOException ex) {
                log.warn("Could not delete old backup {}: {}", p, ex.getMessage());
            }
        }
        return n;
    }

    private static Instant lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toInstant();
        } catch (IOException ex) {
            return Instant.EPOCH;
        }
    }

    /**
     * Extract the on-disk .mv.db path from a {@code jdbc:h2:file:...} URL.
     * Returns null for non-file URLs (tcp, mem) so we can short-circuit.
     */
    private static Path resolveDbFile(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:h2:file:")) return null;
        String rest = jdbcUrl.substring("jdbc:h2:file:".length());
        int semi = rest.indexOf(';');
        if (semi >= 0) rest = rest.substring(0, semi);
        // H2 appends ".mv.db" to the configured file prefix.
        return Paths.get(rest + ".mv.db").toAbsolutePath();
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
