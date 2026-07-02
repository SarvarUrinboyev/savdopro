#!/usr/bin/env bash
# =====================================================================
#  License server H2 -> PostgreSQL data migration (docs/ops/
#  license-server-postgres-migration-plan.md, option A: CSV per table).
#
#  PRECONDITIONS (script checks what it can):
#    1. savdopro-license is STOPPED (H2 file must have no writer).
#    2. The target Postgres DB exists and Flyway has ALREADY built the schema
#       in it — boot the license jar once against the empty DB first (plan
#       step 2), confirm "Successfully applied ... migrations", stop it.
#    3. psql can reach the target (PG* env below).
#
#  USAGE (on the droplet):
#    export PGHOST=localhost PGDATABASE=savdopro_license \
#           PGUSER=savdopro_license PGPASSWORD='...'
#    ./migrate-license-h2-to-pg.sh /opt/savdopro/license-data \
#        /opt/savdopro/savdopro-license-server.jar
#
#  Arg 1: H2 file path WITHOUT the .mv.db suffix
#  Arg 2: license-server jar (supplies the H2 driver classes)
#
#  The H2 file is opened read-only — the script never mutates it, so
#  rollback stays trivial (point license.env back at H2).
# =====================================================================
set -euo pipefail

H2_BASE="${1:?arg1: H2 file path without .mv.db (e.g. /opt/savdopro/license-data)}"
JAR="${2:?arg2: license-server jar path (for the H2 driver)}"
WORK="${WORK_DIR:-/tmp/license-h2pg-$$}"

# FK dependency order: parents before children.
TABLES=(accounts app_users refresh_tokens admin_audit_log payments)

[ -f "${H2_BASE}.mv.db" ] || { echo "ERROR: ${H2_BASE}.mv.db not found"; exit 1; }
[ -f "$JAR" ] || { echo "ERROR: jar not found: $JAR"; exit 1; }
command -v psql >/dev/null || { echo "ERROR: psql not installed"; exit 1; }
if systemctl is-active --quiet savdopro-license 2>/dev/null; then
    echo "ERROR: savdopro-license is RUNNING — stop it first (systemctl stop savdopro-license)"
    exit 1
fi
psql -tAc "SELECT 1" >/dev/null || { echo "ERROR: cannot reach Postgres (check PG* env)"; exit 1; }
# Refuse to load into a non-empty target — protects against double-runs.
existing=$(psql -tAc "SELECT COALESCE(SUM(c),0) FROM (
    SELECT count(*) c FROM accounts UNION ALL SELECT count(*) FROM app_users) t")
if [ "${existing:-0}" -gt 0 ]; then
    echo "ERROR: target already has data (accounts+app_users rows: $existing)."
    echo "       This script only loads into a FRESH Flyway-built schema."
    exit 1
fi

mkdir -p "$WORK"
chmod 700 "$WORK"
trap 'rm -rf "$WORK"' EXIT

# The Spring Boot fat jar nests dependencies, so -cp won't see H2 directly;
# use the PropertiesLauncher-free path: unpack just the H2 jar once.
H2_JAR=$(find "$WORK" -name 'h2-*.jar' 2>/dev/null | head -1 || true)
if [ -z "$H2_JAR" ]; then
    unzip -o -q -j "$JAR" 'BOOT-INF/lib/h2-*.jar' -d "$WORK"
    H2_JAR=$(find "$WORK" -name 'h2-*.jar' | head -1)
fi
[ -n "$H2_JAR" ] || { echo "ERROR: could not extract H2 driver from $JAR"; exit 1; }

h2sql() {
    java -cp "$H2_JAR" org.h2.tools.Shell \
        -url "jdbc:h2:file:${H2_BASE};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;ACCESS_MODE_DATA=r" \
        -user "${H2_USER:-LICENSE}" -password "${H2_PASSWORD:-LICENSE}" \
        -sql "$1" >/dev/null
}

echo "== 1/4 Export from H2 (read-only) =="
for t in "${TABLES[@]}"; do
    h2sql "CALL CSVWRITE('${WORK}/${t}.csv', 'SELECT * FROM ${t}')"
    echo "   exported ${t}.csv ($(wc -l < "${WORK}/${t}.csv") lines incl. header)"
done

echo "== 2/4 Load into Postgres (FK order) =="
for t in "${TABLES[@]}"; do
    psql -v ON_ERROR_STOP=1 -c "\\copy ${t} FROM '${WORK}/${t}.csv' CSV HEADER"
    echo "   loaded ${t}"
done

echo "== 3/4 Reset identity sequences =="
for t in "${TABLES[@]}"; do
    psql -v ON_ERROR_STOP=1 -tAc "SELECT setval(pg_get_serial_sequence('${t}','id'),
        (SELECT COALESCE(MAX(id),1) FROM ${t}))" >/dev/null \
        && echo "   sequence reset: ${t}" \
        || echo "   (no id sequence on ${t} — skipped)"
done

echo "== 4/4 Row-count parity =="
fail=0
for t in "${TABLES[@]}"; do
    # H2 count via CSV (lines minus header) — the JVM is closed, file untouched.
    h2=$(( $(wc -l < "${WORK}/${t}.csv") - 1 ))
    pg=$(psql -tAc "SELECT count(*) FROM ${t}")
    if [ "$h2" = "$pg" ]; then
        echo "   OK  ${t}: ${pg}"
    else
        echo "   MISMATCH ${t}: H2=${h2} PG=${pg}"; fail=1
    fi
done
[ "$fail" = "0" ] || { echo "PARITY FAILED — do NOT cut over."; exit 1; }

cat <<'NEXT'

Parity OK. Next (plan steps 5-6):
  1. Edit /etc/savdopro/license.env -> the 4 SPRING_DATASOURCE_* lines to
     Postgres + HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
  2. systemctl start savdopro-license && journalctl -fu savdopro-license
     (expect clean Flyway validate + Started)
  3. Smoke: real login, /api/auth/refresh, billing page, admin panel.
  4. Rollback = point license.env back at H2 (file was opened read-only).
NEXT
