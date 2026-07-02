# Backup Commands

## Fresh migration backups already taken on the OLD server (2026-07-01 17:14)
| File (on old server, `/root/`) | Size | What |
|---|---|---|
| `barakat_migration_20260701_1714.dump` | 767,053 bytes | PostgreSQL `barakat` (custom format) |
| `license-data_migration_20260701_1714.mv.db` | 188,416 bytes | License H2 file copy |

> These live on the OLD server only. They contain data + auth secrets (password
> hashes, tokens) — **transfer them directly server→server via `scp`, do NOT put
> them in this handoff folder or any repo.**

## Re-create them at any time (read-only, safe on a running box)
```bash
# PostgreSQL — custom format (compressed, restore with pg_restore)
sudo -u postgres pg_dump -Fc -d barakat > /root/barakat_migration_$(date +%Y%m%d_%H%M).dump
ls -l /root/barakat_migration_*.dump        # verify exists + size > 0

# License H2 — live file copy (test/pilot data; H2 recovers on open).
# For a GUARANTEED-consistent copy, stop the license service first (brief
# downtime): systemctl stop savdopro-license && cp -a ... && systemctl start ...
cp -a /opt/savdopro/license-data.mv.db /root/license-data_migration_$(date +%Y%m%d_%H%M).mv.db
ls -l /root/license-data_migration_*.mv.db  # verify exists + size > 0
```

## Transfer to the NEW server (run from the new server, or via a laptop relay)
```bash
scp root@OLD_HOST:/root/barakat_migration_20260701_1714.dump      /root/
scp root@OLD_HOST:/root/license-data_migration_20260701_1714.mv.db /root/
```

## Verify a dump before trusting it
```bash
pg_restore --list /root/barakat_migration_20260701_1714.dump | head   # non-empty TOC = good
```
