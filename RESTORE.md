# Backup restore runbook & drill

BackupService already takes daily snapshots (prod: monitors an external `pg_dump`;
desktop: H2 `BACKUP TO` zip), retains 30 days, and alerts to Telegram. This is the
**other half** — proving a backup actually restores, on a schedule, without touching
production data.

## Why a drill

A backup you have never restored is a hope, not a backup. Run the drill monthly (and
after any schema migration) so a real outage isn't the first time you restore.

---

## Production (PostgreSQL)

Backups: `pg_dump` files in `$BACKUP_DIR` (e.g. `/opt/barakat/backups/pg-YYYY-MM-DD.sql.gz`).

### Drill (safe — restores into a throwaway DB, never prod)

```bash
BACKUP_DIR=/opt/barakat/backups \
DB_HOST=localhost DB_USER=savdopro \
PGPASSWORD=*** \
ops/restore-drill.sh
```

The script: picks the newest dump → creates a scratch DB `savdopro_restore_drill`
→ restores into it → validates (`flyway_schema_history` rows, `products` row count,
latest migration version) → prints **PASS/FAIL** → drops the scratch DB.

### Real restore (disaster recovery)

```bash
systemctl stop savdopro-backend
createdb -h "$DB_HOST" -U "$DB_USER" savdopro_restored
gunzip -c /opt/barakat/backups/pg-<date>.sql.gz | psql -h "$DB_HOST" -U "$DB_USER" savdopro_restored
# point spring.datasource.url at savdopro_restored (or rename), then:
systemctl start savdopro-backend
# verify: curl -s localhost:8086/actuator/health  => {"status":"UP"}
```

Flyway will NOT re-run migrations on a restored DB (the `flyway_schema_history` came
with the dump). Confirm the app boots (a failed migration would block startup).

---

## Desktop (embedded H2)

Backups: `barakat-YYYY-MM-dd-HHmm.zip` in `./backups/` (H2 `BACKUP TO`).

### Drill (PowerShell, Windows)

```powershell
$env:BACKUP_DIR = "$HOME\.barakat\backups"
.\ops\restore-drill.ps1
```

Extracts the newest backup into a scratch folder, opens the `barakat.mv.db` read-only
with the H2 driver, runs a sanity `SELECT count(*)`, prints PASS/FAIL, deletes the scratch.

### Real restore

1. Close the desktop app (releases the H2 file lock).
2. Replace `~/.barakat/barakat.mv.db` with the one extracted from the backup zip.
3. Relaunch — the app opens the restored DB.

---

## Drill checklist (record each run)

- [ ] Newest backup found and not stale (< 26h old)
- [ ] Restore into scratch DB completed without error
- [ ] `flyway_schema_history` present, latest version == expected (e.g. V31)
- [ ] Core tables have rows (`products`, `sales`, `gl_journal_entry`)
- [ ] Scratch DB dropped / scratch folder deleted
- [ ] Result logged (date, backup file, PASS/FAIL) below

| Date | Backup file | Version | Result |
|------|-------------|---------|--------|
|      |             |         |        |
