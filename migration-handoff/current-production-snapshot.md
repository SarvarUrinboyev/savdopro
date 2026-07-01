# Current Production Snapshot (source of truth for the migration)

Captured read-only from the OLD server on **2026-07-01 ~17:14**.

## Release & platform
- App release: **v2.3.4** (production, hardened, prod profile active)
- OS: Ubuntu 24.04.3 LTS · Java 21.0.11 · PostgreSQL 16.14 · nginx 1.24.0

## Services
| Service | Bind | Jar | Notes |
|---|---|---|---|
| `savdopro-backend` | 127.0.0.1:**8086** | `/opt/barakat/barakat-market.jar` (~93 MB) | prod profile, `ddl-auto=none`, User=root |
| `savdopro-license` | 127.0.0.1:**9090** | `/opt/savdopro/savdopro-license-server.jar` (~62 MB) | H2 file, User=root |
| nginx | :80, :443 | — | sole public ingress; `server_name 167-172-164-214.nip.io` (Let's Encrypt). :80 also used as a cloudflared-tunnel target. |

## Databases
- Backend: PostgreSQL **`barakat`** (owner role `barakat`)
- License: H2 file `/opt/savdopro/license-data.mv.db`

## Data
| Item | Value |
|---|---|
| accounts | 13 |
| shops | 15 |
| total products | 6618 |
| target account_id / shop_id | 1003 / 6 |
| shop 6 products | 6598 |
| shop 6 ledger entries / lines | 2780 / 5560 |
| shop 6 inventory value | 262,557,334.87 (trial + balance sheet balanced) |

## Config that must be reproduced (see the templates)
- Backend JVM: `-Xmx320m -Xms128m -XX:+UseSerialGC -XX:MaxRAM=380M` (1 GB box; relax on 2–4 GB)
- License JVM: `-Xmx200m -Xms64m -XX:+UseSerialGC -XX:MaxRAM=240M`
- Prod flags (backend): `SPRING_PROFILES_ACTIVE=prod`, `WEB_ALLOWED_ORIGINS`,
  `SPRING_JPA_HIBERNATE_DDL_AUTO=none`, `SERVER_ADDRESS=127.0.0.1`
- License flags: `FORWARD_HEADERS_STRATEGY=framework`, `SERVER_ADDRESS=127.0.0.1`
- Secrets on the old box live in `/etc/systemd/system/*.service.d/*.conf` drop-ins
  (`sms.conf`, `alert.conf`, `oauth.conf`) and `/opt/barakat/application-local.properties`
  — **their contents were NOT read/exported.** On the new box put the same *keys*
  into the 0600 env files (values sent separately).

## Fresh migration backups (on old server, `/root/`)
- `barakat_migration_20260701_1714.dump` — 767,053 bytes
- `license-data_migration_20260701_1714.mv.db` — 188,416 bytes

## Known non-blocking follow-ups (carry to the new box)
- Rotate the **owner password** (typed in chat earlier) and the **Eskiz SMS password**.
- Services run as **root** — move to a dedicated user + `ProtectSystem` when convenient.
- `ddl-auto=none` — can switch to `validate` after a staging schema check.
- License **H2 → PostgreSQL** is a later P1 (not needed for this migration).
- Raise nginx `proxy_read_timeout` on `/api/accounting/backfill` for big backfills
  (already handled in the nginx template).
