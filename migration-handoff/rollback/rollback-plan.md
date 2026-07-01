# Rollback Plan

**Golden rule: the OLD server stays online, untouched, and authoritative until
the NEW server passes every check in `verification/verification-checklist.md`.**
Nothing is "cut over" until then. DNS is not changed as part of this migration.

## During setup (new server not yet live)
Nothing to roll back — the new server is being built in parallel. If a step
fails, fix it on the new server; the old server keeps serving users.

## If the NEW server fails verification
1. Do **not** point users/traffic at it. Keep using the OLD server.
2. Investigate on the new server (logs, counts, env, ports).
3. Re-restore the DB / re-copy the H2 file if counts are wrong
   (`database/restore-commands.md`) — never edit the old server to compensate.

## If a bad jar is deployed to the NEW server
```bash
# restore the previous jar (CI keeps *.bak-ci; or re-copy from the OLD server):
cp /opt/barakat/barakat-market.jar.bak-ci /opt/barakat/barakat-market.jar 2>/dev/null
cp /opt/savdopro/savdopro-license-server.jar.bak-ci /opt/savdopro/savdopro-license-server.jar 2>/dev/null
systemctl restart savdopro-license savdopro-backend
# or redeploy the previous good tag via GitHub Actions.
```

## If you already cut over and must revert
- Point the ingress (Cloudflare tunnel / reverse proxy / whatever routes the
  public host) back to the OLD server. **Do not** change DNS as part of this
  task — coordinate with the owner.
- The OLD server's data is unchanged (this migration only READ from it + took
  backups), so reverting loses only whatever was entered on the NEW server after
  cutover. Keep the cutover window short and freeze data entry during it.

## Backups to restore from (on the OLD server, `/root/`)
- `barakat_migration_20260701_1714.dump` (767,053 bytes)
- `license-data_migration_20260701_1714.mv.db` (188,416 bytes)

## STOP conditions (halt the migration, keep OLD server live)
- DB dump or license H2 file missing / zero-size
- restored counts don't match `database/expected-counts.md`
- env file incomplete (app won't boot)
- backend or license health ≠ 200
- login fails
- product count mismatch (≠ 6618 total / 6598 shop 6)
- accounting report fails or trial/balance sheet not balanced
- 8086 or 9090 reachable from outside (public port exposure)
- security headers missing / OpenAPI JSON exposed
