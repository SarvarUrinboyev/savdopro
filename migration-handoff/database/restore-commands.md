# Restore Commands (NEW server)

## 1. PostgreSQL — create role + database, then restore
```bash
sudo -u postgres psql <<'SQL'
CREATE ROLE barakat LOGIN PASSWORD '__SET_A_STRONG_PASSWORD__';
CREATE DATABASE barakat OWNER barakat;
SQL

# Restore the custom-format dump INTO the empty barakat DB.
sudo -u postgres pg_restore --no-owner --role=barakat -d barakat \
     /root/barakat_migration_20260701_1714.dump

# The dump already contains the full schema (Flyway V1..V37) + all data, so the
# backend boots with ddl-auto=none and Flyway finds nothing new to run.
```
Put the same password into `SPRING_DATASOURCE_PASSWORD` in `/etc/savdopro/backend.env`.

## 2. License H2 — just place the file
```bash
mkdir -p /opt/savdopro
systemctl stop savdopro-license 2>/dev/null || true         # if already installed
cp -a /root/license-data_migration_20260701_1714.mv.db /opt/savdopro/license-data.mv.db
chown root:root /opt/savdopro/license-data.mv.db             # (or the service user)
# The URL jdbc:h2:file:/opt/savdopro/license-data opens this exact file.
```

## 3. Verify the restore (counts must match database/expected-counts.md)
```bash
sudo -u postgres psql -d barakat -tAc \
 "SELECT 'accounts',count(*) FROM accounts
   UNION ALL SELECT 'shops',count(*) FROM shops
   UNION ALL SELECT 'products',count(*) FROM products
   UNION ALL SELECT 'shop6',count(*) FROM products WHERE shop_id=6
   UNION ALL SELECT 'gl_entries_shop6',count(*) FROM gl_journal_entry WHERE shop_id=6;"
```

## Rollback of a bad restore
The dump is immutable; to redo, drop and recreate:
```bash
sudo -u postgres psql -c "DROP DATABASE barakat;" -c "CREATE DATABASE barakat OWNER barakat;"
# then pg_restore again.
```
(Never drop the OLD server's database — it stays as the live fallback.)
