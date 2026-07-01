# SavdoPRO — Production Hardening Runbook

**Scope:** apply the prod-profile + security fixes that ship in the `v2.3.4`
release to the live droplet, safely, with verification and rollback.

**Server:** DigitalOcean `167.172.164.214` · nip.io HTTPS · nginx sole ingress
· services `savdopro-backend` (8086) and `savdopro-license` (9090).

> **Author's honesty note.** These commands were prepared **offline** from the
> repo. They were **not** run against production during authoring, and the exact
> live `systemd` unit contents / nginx server block were **not** read over SSH.
> Every step below is either read-only (safe to run any time) or a **mutation
> that requires founder approval** and reconciliation with `systemctl cat` /
> the live nginx config first. Nothing here restarts prod automatically.

Legend: 🔍 read-only · ✋ mutation — needs founder go-ahead · ⏪ rollback

---

## 0. Pre-flight (🔍 read-only — run these first, capture output)

```bash
ssh -i ~/.ssh/savdopro_vps root@167.172.164.214
# What profile / user / env / ExecStart are the services really running with?
systemctl show savdopro-backend  -p ExecStart -p Environment -p EnvironmentFiles -p User -p Group
systemctl show savdopro-license  -p ExecStart -p Environment -p EnvironmentFiles -p User -p Group
systemctl cat  savdopro-backend
systemctl cat  savdopro-license
# Config files + permissions (do NOT cat secret files)
ls -l /opt/barakat /opt/savdopro /etc/savdopro 2>/dev/null
stat -c '%A %U:%G %n' /opt/barakat/application-local.properties 2>/dev/null
# Is the prod profile active + is Swagger exposed? (should become 404 after fix)
curl -s -o /dev/null -w '%{http_code}\n' https://167-172-164-214.nip.io/v3/api-docs
# Current schema-management setting actually in effect
grep -i 'ddl-auto\|profiles.active' /opt/barakat/application-local.properties 2>/dev/null || echo "no local override lines"
# Take a full DB backup BEFORE any change (backup.sh already runs 03:30 daily)
/opt/barakat/backup.sh && ls -lh /opt/barakat/backups | tail -3
```

**Expected findings that this runbook fixes:** profile is `local` (not `prod`),
`User=root`, no `EnvironmentFile`, `/v3/api-docs` → `200`, and a
world-readable `application-local.properties` possibly carrying `ddl-auto=update`.

---

## Root cause (why prod is mis-profiled)

`vps-setup.sh` (and the units derived from its pattern) sets
`--spring.profiles.active=local` **whenever `application-local.properties`
exists** — which it does on the droplet. So prod boots under the **`local`**
profile and `application-prod.properties` (Swagger-off, Postgres, CORS
fail-closed, strict CSP, HSTS scope, demo-seed hard-off) **never loads**, while
the world-readable local file overrides schema management. The fix is to
activate **`prod`** in one place (env), move secrets into a `0600` env file, and
remove the local override from the prod box.

---

## 1. Create dedicated service users (✋)

```bash
# System accounts, no shell, no home. Idempotent.
id savdopro         >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin savdopro
id savdopro-license >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin savdopro-license
```

## 2. Own the runtime dirs (✋)

```bash
mkdir -p /var/log/savdopro
chown -R savdopro:savdopro                 /opt/barakat  /var/log/savdopro
chown -R savdopro-license:savdopro-license /opt/savdopro
# The root CI deploy (scp + mv + systemctl) still works — root overrides owner.
# The license H2 file must stay writable by its service user:
ls -l /opt/savdopro/license-data.mv.db 2>/dev/null
```

## 3. Put secrets in 0600 env files (✋)

```bash
mkdir -p /etc/savdopro && chmod 750 /etc/savdopro
# Copy the templates from the repo (ops/systemd/*.example) to:
#   /etc/savdopro/backend.env      and   /etc/savdopro/license.env
# Fill real values by MOVING them out of /opt/barakat/application-local.properties
# (DB password, JWT secret, telegram token, admin password, chat id).
chown root:savdopro          /etc/savdopro/backend.env && chmod 600 /etc/savdopro/backend.env
chown root:savdopro-license  /etc/savdopro/license.env && chmod 600 /etc/savdopro/license.env
```

Then **neutralise the prod local override** so it can no longer override the
prod profile (this is what removes `ddl-auto=update` and re-exposes Swagger):

```bash
# Keep a copy, then remove from the working dir the backend reads.
cp /opt/barakat/application-local.properties /root/application-local.properties.bak-$(date +%F)
rm /opt/barakat/application-local.properties
```

> Also fix the world-readable **license alert drop-in** flagged in the audit
> (one file was `644`). Find it and tighten:
> `find /etc/systemd/system/savdopro-license.service.d -type f -exec chmod 600 {} \;`
> (env-bearing drop-ins should be `600`; a pure unit override with no secrets can
> stay `644`, but move secrets into `license.env` regardless).

## 4. Install the hardened systemd drop-ins (✋)

Copy `ops/systemd/savdopro-backend.service.d/10-hardening.conf.example` and
`ops/systemd/savdopro-license.service.d/10-hardening.conf.example` into
`/etc/systemd/system/<unit>.service.d/10-hardening.conf`.

**Before saving, reconcile `ExecStart` with `systemctl cat` output** (step 0):
keep the box's real JVM flags, and ensure **no `--spring.profiles.active=local`
remains** — the profile now comes from `SPRING_PROFILES_ACTIVE=prod` in
`backend.env`. Key directives the drop-ins set:

- `User=` / `Group=` → the dedicated accounts
- `EnvironmentFile=/etc/savdopro/{backend,license}.env`
- `server.address=127.0.0.1` is inherited (backend) / set via `SERVER_ADDRESS`
  (license) so both bind loopback only; nginx is the sole ingress
- `NoNewPrivileges`, `ProtectSystem=strict`, `ProtectHome`, `PrivateTmp`,
  `ReadWritePaths` (see the drop-in comments)

## 5. Reload systemd (✋)

```bash
systemctl daemon-reload
```

## 6. Restart — license first, backend second (✋ — founder approval)

```bash
systemctl restart savdopro-license
for i in $(seq 1 30); do sleep 2; curl -sf http://127.0.0.1:9090/actuator/health >/dev/null && break; done
systemctl restart savdopro-backend
for i in $(seq 1 40); do sleep 3; curl -sf http://127.0.0.1:8086/actuator/health >/dev/null && break; done
systemctl status savdopro-license savdopro-backend --no-pager | grep -E 'Active:|Main PID'
journalctl -u savdopro-backend -n 40 --no-pager   # confirm "The following 1 profile is active: prod"
```

## 7. Verify (🔍)

```bash
# Profile active + Swagger gone.
journalctl -u savdopro-backend -n 80 --no-pager | grep -i 'profile is active'   # want "prod"
# IMPORTANT: /v3/api-docs stays HTTP 200 even when disabled — the SPA catch-all
# serves index.html for unknown paths. The real signal is the CONTENT-TYPE / body:
#   disabled  -> text/html  (SPA shell)          [want this]
#   EXPOSED   -> application/json  {"openapi":...}[the finding — must NOT see this]
curl -s -D- -o /tmp/d https://167-172-164-214.nip.io/v3/api-docs | grep -i '^content-type'
grep -q '"openapi"' /tmp/d && echo 'FAIL: OpenAPI schema still exposed' || echo 'OK: api-docs is SPA shell (springdoc disabled)'
curl -s https://167-172-164-214.nip.io/v3/api-docs/swagger-config | grep -qi '<!DOCTYPE html' \
  && echo 'OK: swagger-config is SPA shell' || echo 'CHECK: swagger-config returned non-HTML'
# Health up
curl -s -o /dev/null -w 'health=%{http_code}\n'    https://167-172-164-214.nip.io/actuator/health     # want 200
# Unauthorized API is 401
curl -s -o /dev/null -w 'products=%{http_code}\n'  https://167-172-164-214.nip.io/api/products         # want 401
# CORS: evil origin must NOT be allowed (no access-control-allow-origin echoed)
curl -s -D- -o /dev/null -X OPTIONS \
  -H 'Origin: https://evil.example' -H 'Access-Control-Request-Method: POST' \
  https://167-172-164-214.nip.io/api/products | grep -i 'access-control-allow-origin' \
  && echo 'FAIL: evil origin allowed' || echo 'OK: evil origin rejected'
# Security headers present
curl -s -D- -o /dev/null https://167-172-164-214.nip.io/ | grep -iE \
  'strict-transport-security|content-security-policy|referrer-policy|permissions-policy|x-frame-options|x-content-type-options'
# Ports bound to loopback only
ss -tlnp | grep -E ':8086|:9090'   # want 127.0.0.1:8086 and 127.0.0.1:9090
# Live frontend loads (index shell 200)
curl -s -o /dev/null -w 'spa=%{http_code}\n'       https://167-172-164-214.nip.io/                     # want 200
```

Sign-off requires: profile=prod, api-docs/swagger=404, health=200, products=401,
evil-origin rejected, all six headers present, both ports on 127.0.0.1, SPA=200.

## 8. Rollback (⏪)

```bash
# Revert the systemd changes
rm /etc/systemd/system/savdopro-backend.service.d/10-hardening.conf
rm /etc/systemd/system/savdopro-license.service.d/10-hardening.conf
# Restore the previous override file if the app needs it to boot
cp /root/application-local.properties.bak-YYYY-MM-DD /opt/barakat/application-local.properties
systemctl daemon-reload
systemctl restart savdopro-license savdopro-backend
# If the new JAR is implicated, redeploy the previous good tag's jars:
#   from CI: re-run Deploy on tag v2.3.3   (or scp the *.bak-ci jars back)
cp /opt/barakat/barakat-market.jar.bak-ci /opt/barakat/barakat-market.jar 2>/dev/null
cp /opt/savdopro/savdopro-license-server.jar.bak-ci /opt/savdopro/savdopro-license-server.jar 2>/dev/null
systemctl restart savdopro-license savdopro-backend
```

---

## Phase 4 — Validate `ddl-auto=validate` before the switch

**Why:** prod historically ran `ddl-auto=update` (see `Sale.java`, `Shift.java`,
`V29__schema_catchup_entity_columns.sql`). `validate` asserts the entities'
required tables/columns exist and **fails startup** if any are *missing*
(extra drift columns are tolerated). The repo default for prod is now
`validate`, gated behind `SPRING_JPA_HIBERNATE_DDL_AUTO`.

**Local evidence gathered during authoring:** the backend boots under the
**`prod` profile with `validate`** against a fresh Flyway-migrated schema
(see the release notes / final report for the exact run). This proves the code
is self-consistent: every JPA-required column is created by Flyway `V1..V37`.

**What it does NOT prove:** that the *live* Postgres schema has no *missing*
column vs. the entities. It almost certainly does not (prod ran the same Flyway
set), but confirm on a clone before flipping:

```bash
# On a STAGING box or a throwaway Postgres — NEVER on prod:
pg_dump --schema-only "$PROD_DSN" > /tmp/prod-schema.sql      # schema only, no data
createdb savdopro_validate && psql savdopro_validate < /tmp/prod-schema.sql
# Boot the v2.3.4 backend jar against it with prod profile + validate:
SPRING_PROFILES_ACTIVE=prod DB_URL=jdbc:postgresql://localhost:5432/savdopro_validate \
  DB_USER=... DB_PASSWORD=... WEB_ALLOWED_ORIGINS=https://167-172-164-214.nip.io \
  SAVDOPRO_JWT_SECRET=... java -jar barakat-market.jar
# Success = "Started ... in Ns" with no "Schema-validation: missing table/column".
```

**Rollout order for schema safety:**
1. First prod-profile deploy with `SPRING_JPA_HIBERNATE_DDL_AUTO=none` in
   `backend.env` → guarantees no mutation, no validate startup risk.
2. Confirm healthy, then run the staging validate check above.
3. Remove the `none` override (→ `validate` default), restart, confirm boot.

---

## Phase 6 — Telegram alert fix (`chat not found`)

**Root cause:** the configured alert chat id (e.g. `5035317446`) never sent
`/start` to the bot, so Telegram rejects `sendMessage` with HTTP 400
`chat not found`. The senders are fire-and-forget and only `WARN`-log the body
(no retry storm, no user impact), but every event type (watchdog, off-site
backup, daily report, lockout) hits the same bad chat, so the WARN repeats.
**Neither the Java sender nor the shell scripts log the bot token** — do not
print it here either.

**Validate token + discover the real chat id (🔍 — token never printed):**

```bash
# Read the token from the 0600 env file into a shell var; never echo it.
set +o history
TOKEN=$(grep -E '^TELEGRAM_BOT_TOKEN=' /etc/savdopro/backend.env | cut -d= -f2-)
# 1) Token valid? shows the bot username, not the token:
curl -s "https://api.telegram.org/bot$TOKEN/getMe" | python3 -c 'import sys,json;d=json.load(sys.stdin);print("ok",d.get("ok"),d.get("result",{}).get("username"))'
# 2) Ask the founder to open the bot and press START, then read the chat id:
curl -s "https://api.telegram.org/bot$TOKEN/getUpdates" | python3 -c 'import sys,json;[print("chat.id=",u.get("message",{}).get("chat",{}).get("id"),u.get("message",{}).get("chat",{}).get("type")) for u in json.load(sys.stdin).get("result",[])]'
unset TOKEN; set -o history
```

**Set the verified id + test delivery (✋ — token stays in the var):**

```bash
# Put the real numeric id into TELEGRAM_CHAT_IDS in /etc/savdopro/backend.env
# (and SAVDOPRO_LICENSE_ALERT_CHAT_ID in license.env), then:
TOKEN=$(grep -E '^TELEGRAM_BOT_TOKEN=' /etc/savdopro/backend.env | cut -d= -f2-)
CHAT=$(grep -E '^TELEGRAM_CHAT_IDS=' /etc/savdopro/backend.env | cut -d= -f2- | cut -d, -f1)
curl -s -o /dev/null -w 'send=%{http_code}\n' \
  --data-urlencode "chat_id=$CHAT" --data-urlencode 'text=SavdoPRO alert test ✅' \
  "https://api.telegram.org/bot$TOKEN/sendMessage"   # want 200
unset TOKEN CHAT
systemctl restart savdopro-backend   # pick up the new env
```

**Silence noisy alerts temporarily** (if needed before the id is fixed): set
`TELEGRAM_ENABLED=false` in `backend.env` and blank `SAVDOPRO_LICENSE_ALERT_CHAT_ID`
in `license.env`, then restart. The lockout/anomaly logic still runs and logs;
only the (failing) Telegram send is skipped.

**Where the chat id / token live:** both go in the `0600` env files
(`/etc/savdopro/*.env`), never in a `644` properties file. The chat id is not
secret; the bot token is. Also correct the id in the shell scripts that send
directly (`/opt/barakat/watch.sh`, `backup.sh`) so off-site backup + uptime
alerts resolve too.
