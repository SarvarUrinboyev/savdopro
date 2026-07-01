# License Server — H2 → PostgreSQL Migration Plan

**Status:** PLAN ONLY. Do **not** execute during the `v2.3.4` hardening or the
Excel import. This is a separate, scheduled change.

## Why

Production `savdopro-license` runs on an embedded **H2 file DB**
(`/opt/savdopro/license-data.mv.db`). H2-file is single-process, has no
point-in-time recovery, no concurrent-writer safety, and a backup is just a raw
file copy that can tear if copied while the JVM holds it open. The license
server is the **identity + billing system of record** (accounts, users, password
hashes, refresh sessions, subscription/license status, payments) — exactly the
data that should live on managed Postgres before real SaaS scale.

Good news: the code is already Postgres-ready — the PostgreSQL driver is on the
classpath, `ddl-auto=validate`, and the datasource URL/driver/dialect are all
env-overridable. `docker/postgres-init.sql` already creates a separate `license`
database. **Only the data has to move; Flyway rebuilds the schema.**

## Is it a blocker?

| Question | Answer |
|---|---|
| **P0 blocker for tomorrow's Excel import?** | **NO.** The import writes products into the **backend's Postgres**, not the license DB. Auth/accounts are untouched by the import. |
| **P1 before real SaaS scale?** | **YES.** Do it soon after `v2.3.4` + import, before onboarding more tenants. |
| Recommended order | `v2.3.4` deploy → Excel import → **then** this migration as its own change. |

## Current state

- File: `/opt/savdopro/license-data.mv.db` (+ `.trace.db` if present)
- Schema: Flyway `V1..V13` (accounts, app_users, admin_audit_log,
  refresh_tokens, payments, + TOTP/MFA, white-label, account modules, telegram
  OAuth, SMS phone, user permissions, account plan, Payme transactions)
- Access mode: `jdbc:h2:file:...;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`

## Target

- Managed or co-located PostgreSQL, **separate database** `savdopro_license`
  (never share the backend's DB — both define `accounts`/`app_users`).
- Role `savdopro_license` with a strong password (goes in `/etc/savdopro/license.env`).

## Procedure

### 1. Backup (both sides)
```bash
systemctl stop savdopro-license                     # quiesce writers first
cp -a /opt/savdopro/license-data.mv.db /root/license-data.$(date +%F).mv.db.bak
# (Postgres side is empty at this point.)
```

### 2. Build the target schema with Flyway (not a schema dump)
Point a throwaway/staging license-server at the empty Postgres DB and let it
migrate — this guarantees the PG schema is byte-identical to what the code
expects, with no H2→PG DDL translation:
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/savdopro_license \
SPRING_DATASOURCE_USERNAME=savdopro_license SPRING_DATASOURCE_PASSWORD=... \
SPRING_DATASOURCE_DRIVER=org.postgresql.Driver \
HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect \
SAVDOPRO_JWT_SECRET=... java -jar savdopro-license-server.jar
# Confirm "Successfully applied 13 migrations", then stop it.
```

### 3. Export data from H2, load into Postgres
Schema already exists (step 2), so move **data only**, table by table, in FK
order (`accounts` → `app_users` → children). Two options:

**A. CSV per table (simple, auditable).** With the license JVM stopped, open the
H2 file read-only and dump each table, then `\copy` into PG:
```sql
-- H2 (via the H2 shell against the file, read-only):
CALL CSVWRITE('/tmp/accounts.csv', 'SELECT * FROM accounts');
-- ...repeat per table in dependency order...
```
```bash
# Postgres:
psql -d savdopro_license -c "\copy accounts FROM '/tmp/accounts.csv' CSV HEADER"
# ...repeat per table...
# Reset identity sequences so new inserts don't collide:
psql -d savdopro_license -c "SELECT setval(pg_get_serial_sequence('accounts','id'), (SELECT COALESCE(MAX(id),1) FROM accounts));"
# ...repeat for every table with a serial/identity id...
```

**B. JDBC copier (repeatable).** A tiny script that, per table, `SELECT *` from
H2 and batch-`INSERT` into PG preserving ids, then fixes sequences. Prefer this
if the data volume or column types (booleans, timestamps, bytea for hashes) make
CSV fiddly. Keep it in `ops/` if written.

### 4. Verify parity
```bash
# Row counts must match per table:
for t in accounts app_users admin_audit_log refresh_tokens payments; do
  echo -n "$t H2=?  PG="; psql -tAc "SELECT count(*) FROM $t" savdopro_license
done
# Spot-check: a known admin can still log in (password hash intact), a known
# account's subscription_expires / license status is correct, and a recent
# payment row is present.
```

### 5. Cutover
1. In `/etc/savdopro/license.env`, swap the four `SPRING_DATASOURCE_*` lines from
   H2 to the Postgres values (+ `HIBERNATE_DIALECT=...PostgreSQLDialect`).
2. `systemctl restart savdopro-license`; watch `journalctl` for a clean
   `validate` + `Started`.
3. Smoke test: login, `/api/auth/refresh`, a billing endpoint, admin panel.
4. After a healthy soak, `/opt/savdopro` can become read-only (drop the H2
   `ReadWritePaths` from the systemd drop-in).

### 6. Rollback to H2
The H2 file was never modified (we only read it). To revert: set the
`SPRING_DATASOURCE_*` lines in `license.env` back to the H2 values and
`systemctl restart savdopro-license`. Because both schemas are Flyway-built and
in sync, no data conversion is needed on rollback — only writes that happened on
PG after cutover would be lost (hence keep the cutover soak short, or freeze
signups during it).

## Risks

- **Password hashes / MFA secrets / OAuth ids** — must survive byte-for-byte
  (BCrypt strings, TOTP secrets). CSV round-tripping of text columns is safe;
  verify a real login post-migration before declaring success.
- **Refresh sessions** — `refresh_tokens` rows carry live sessions; if not
  migrated, every user is logged out (tolerable, but announce it). Migrate them
  to avoid a mass re-login.
- **Subscription / license status** — `accounts.subscription_expires`,
  `blocked`, plan/module rows gate customer access; a wrong value locks a paying
  shop out or lets a lapsed one in. This is the highest-impact data — verify.
- **Payments / Payme transactions** — financial audit trail; must not be
  dropped or duplicated (sequence reset prevents id collisions).
- **Downtime** — steps 1–5 need the license server stopped. Desktop clients that
  already hold a valid JWT keep working against their local backend during the
  window; only login/refresh/billing/admin are unavailable. Schedule off-hours;
  expect ~15–30 min.

## Recommendation

Do the migration **after** `v2.3.4` deploy and the Excel import, as its own
change with its own backup + verification. It does **not** block the import. Pair
it with managed Postgres (auto-backup / PITR) rather than a co-located instance
if budget allows.
