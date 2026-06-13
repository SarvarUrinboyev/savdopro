#!/usr/bin/env bash
# Backup restore drill (PostgreSQL). Restores the newest pg_dump into a throwaway
# scratch database, validates it, then drops it. NEVER touches the production DB.
# Exit 0 = PASS, non-zero = FAIL. Run monthly + after every schema migration.
#
#   BACKUP_DIR=/opt/barakat/backups DB_HOST=localhost DB_USER=savdopro \
#   PGPASSWORD=*** ops/restore-drill.sh
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/barakat/backups}"
DB_HOST="${DB_HOST:-localhost}"
DB_USER="${DB_USER:-savdopro}"
SCRATCH="${SCRATCH_DB:-savdopro_restore_drill}"
EXPECT_VERSION="${EXPECT_VERSION:-}"   # optional, e.g. 31

echo "== restore drill =="
latest="$(ls -1t "$BACKUP_DIR"/pg-*.sql.gz "$BACKUP_DIR"/pg-*.sql 2>/dev/null | head -1 || true)"
[ -n "$latest" ] || { echo "FAIL: no pg backup found in $BACKUP_DIR"; exit 2; }
echo "backup: $latest"

# Staleness: warn (don't fail) if older than 26h.
if [ "$(find "$latest" -mmin +1560 2>/dev/null)" ]; then
  echo "WARN: newest backup is older than 26h"
fi

psql -h "$DB_HOST" -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS $SCRATCH;" -c "CREATE DATABASE $SCRATCH;"
trap 'psql -h "$DB_HOST" -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS $SCRATCH;" >/dev/null 2>&1 || true' EXIT

echo "restoring into scratch DB $SCRATCH ..."
case "$latest" in
  *.gz) gunzip -c "$latest" | psql -h "$DB_HOST" -U "$DB_USER" -d "$SCRATCH" -q -v ON_ERROR_STOP=1 ;;
  *)    psql -h "$DB_HOST" -U "$DB_USER" -d "$SCRATCH" -q -v ON_ERROR_STOP=1 -f "$latest" ;;
esac

q() { psql -h "$DB_HOST" -U "$DB_USER" -d "$SCRATCH" -tAc "$1" 2>/dev/null | tr -d '[:space:]'; }
ver="$(q "SELECT MAX(version) FROM flyway_schema_history WHERE success;")"
products="$(q "SELECT count(*) FROM products;")"
sales="$(q "SELECT count(*) FROM sales;")"
echo "flyway latest version: ${ver:-<none>}"
echo "products rows: ${products:-?}, sales rows: ${sales:-?}"

[ -n "$ver" ] || { echo "FAIL: no flyway history in restored DB"; exit 3; }
if [ -n "$EXPECT_VERSION" ] && [ "$ver" != "$EXPECT_VERSION" ]; then
  echo "FAIL: expected migration version $EXPECT_VERSION, restored $ver"; exit 4;
fi
echo "PASS: backup restores cleanly (version $ver)"
