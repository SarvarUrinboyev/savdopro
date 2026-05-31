#!/usr/bin/env bash
#
# PostgreSQL backup for SavdoPRO (License Server + merchant backends).
#
# Takes a compressed pg_dump custom-format snapshot, names it with a UTC
# timestamp, and prunes snapshots older than the retention window. Designed
# for cron: it logs to stdout/stderr and exits non-zero on any failure so a
# wrapper / monitoring job notices a missed backup.
#
# Connection comes from the standard libpq env vars (so no secret is baked
# into this file):
#   PGHOST      (default: localhost)
#   PGPORT      (default: 5432)
#   PGUSER      (required, e.g. savdopro)
#   PGPASSWORD  (required — or use a ~/.pgpass file and leave this unset)
#   PGDATABASE  (required, e.g. savdopro_license)
#
# Knobs:
#   BACKUP_DIR      where dumps are written       (default: ./backups)
#   RETENTION_DAYS  prune dumps older than this   (default: 14)
#
# Example cron (daily 02:30, keep 30 days):
#   30 2 * * *  PGUSER=savdopro PGPASSWORD=*** PGDATABASE=savdopro_license \
#               BACKUP_DIR=/var/backups/savdopro RETENTION_DAYS=30 \
#               /opt/savdopro/ops/backup-postgres.sh >> /var/log/savdopro-backup.log 2>&1
#
# Restore a snapshot:
#   pg_restore --clean --if-exists --no-owner -d "$PGDATABASE" <file>.dump
#
set -Eeuo pipefail

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

log() { echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] $*"; }
die() { log "ERROR: $*"; exit 1; }

command -v pg_dump >/dev/null 2>&1 || die "pg_dump not found on PATH"
[ -n "${PGUSER:-}" ]     || die "PGUSER is required"
[ -n "${PGDATABASE:-}" ] || die "PGDATABASE is required"

mkdir -p "$BACKUP_DIR" || die "cannot create BACKUP_DIR=$BACKUP_DIR"

stamp="$(date -u '+%Y%m%d-%H%M%SZ')"
out="${BACKUP_DIR}/${PGDATABASE}-${stamp}.dump"
tmp="${out}.partial"

log "Backing up ${PGUSER}@${PGHOST}:${PGPORT}/${PGDATABASE} -> ${out}"

# -Fc = compressed custom format (restore with pg_restore). Write to a
# .partial file first and rename on success so a crashed dump never looks
# like a complete backup.
if pg_dump --host="$PGHOST" --port="$PGPORT" --username="$PGUSER" \
           --format=custom --no-owner --no-privileges \
           --file="$tmp" "$PGDATABASE"; then
    mv -f "$tmp" "$out"
    size="$(du -h "$out" | cut -f1)"
    log "OK: wrote ${out} (${size})"
else
    rm -f "$tmp"
    die "pg_dump failed for ${PGDATABASE}"
fi

# Prune old snapshots for this database only.
pruned="$(find "$BACKUP_DIR" -maxdepth 1 -type f \
            -name "${PGDATABASE}-*.dump" -mtime "+${RETENTION_DAYS}" -print -delete | wc -l | tr -d ' ')"
log "Pruned ${pruned} snapshot(s) older than ${RETENTION_DAYS} day(s)"
log "Done."
