# SavdoPRO — Server Migration / Deploy Guide (v2.3.4)

Deploy SavdoPRO to a fresh server. Read this top-to-bottom once, then follow the
steps. Everything referenced lives in this `migration-handoff/` folder.

> **Ground rules:** the OLD server stays online and untouched until the NEW one
> passes `verification/verification-checklist.md`. Don't change DNS, don't re-run
> the Excel import or the ledger backfill, and never commit a real secret. Real
> secret **values** are sent separately — this package has **templates only**.

---

## 1. Architecture overview
- **savdopro-backend** (Spring Boot, Java 21) — port **127.0.0.1:8086**. Talks to
  PostgreSQL **`barakat`**; serves the bundled React SPA + `/api/**`.
- **savdopro-license** (Spring Boot, Java 21) — port **127.0.0.1:9090**. Auth,
  signup, billing. Uses an **H2 file** DB (`/opt/savdopro/license-data.mv.db`).
- **nginx** — the only public ingress (HTTPS 443, HTTP 80). Routes
  `/api/(auth|admin|billing)/` → 9090, everything else → 8086. Backend & license
  are loopback-only.
- **Shared JWT secret** — both services must sign/verify with the *same* secret.
- Frontend is bundled **inside** the backend jar (no separate web server).
- Full detail: `current-production-snapshot.md`.

## 2. Required server specs
Ubuntu 24.04, Java 21, PostgreSQL 16, nginx, ufw, certbot. RAM ≥ 2 GB (4 GB
recommended), disk ≥ 40 GB. Public ports **22/80/443 only**; 8086/9090 stay
localhost. See `server-requirements.md`.

## 3. Required packages
```bash
sudo apt update
sudo apt install -y openjdk-21-jdk postgresql nginx certbot python3-certbot-nginx ufw unzip curl
```

## 4. Java / PostgreSQL / nginx setup
```bash
java -version                      # must be 21.x
systemctl enable --now postgresql
mkdir -p /opt/barakat /opt/savdopro /etc/savdopro
# firewall
ufw default deny incoming && ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp && ufw enable
```

## 5. PostgreSQL restore
Create the `barakat` role + database and restore the dump — see
`database/restore-commands.md`. In short:
```bash
sudo -u postgres psql -c "CREATE ROLE barakat LOGIN PASSWORD '__STRONG__';"
sudo -u postgres psql -c "CREATE DATABASE barakat OWNER barakat;"
sudo -u postgres pg_restore --no-owner --role=barakat -d barakat /root/barakat_migration_20260701_1714.dump
```
Then verify counts against `database/expected-counts.md`.

## 6. License H2 restore/copy
```bash
cp -a /root/license-data_migration_20260701_1714.mv.db /opt/savdopro/license-data.mv.db
```
The URL `jdbc:h2:file:/opt/savdopro/license-data` opens this exact file.

## 7. Backend jar deployment
Copy `/opt/barakat/barakat-market.jar` from the old server (or build it) and,
optionally, `/opt/barakat/update.json`. See `github-actions/deploy-options.md` (Option A).

## 8. License jar deployment
Copy `/opt/savdopro/savdopro-license-server.jar` from the old server (or build it).

## 9. systemd setup
- Fill `/etc/savdopro/backend.env` and `/etc/savdopro/license.env` from the
  templates in `env-templates/` (real values sent separately). **chmod 600, root:root.**
- Install the units from `systemd/` to `/etc/systemd/system/`.
```bash
chmod 600 /etc/savdopro/backend.env /etc/savdopro/license.env
systemctl daemon-reload
systemctl enable --now savdopro-license     # license FIRST
systemctl enable --now savdopro-backend     # backend second
journalctl -u savdopro-backend -n 40 --no-pager | grep 'profile is active'   # expect prod
```

## 10. nginx setup
- Copy `nginx/savdopro-nginx.conf.example` → `/etc/nginx/sites-available/savdopro`,
  replace `__NEW_HOST__`, symlink into `sites-enabled/`, get a cert, reload.
```bash
ln -s /etc/nginx/sites-available/savdopro /etc/nginx/sites-enabled/savdopro
sudo certbot --nginx -d __NEW_HOST__
nginx -t && systemctl reload nginx
```

## 11. GitHub Actions deploy option
Set the `DEPLOY_SSH_KEY` / `DEPLOY_HOST` / `DEPLOY_USER` secrets (+ `LICENSE_URL`
var) and push a `v*` tag. Details: `github-actions/github-actions-secrets.md`.
CI only swaps jars; systemd/env/nginx/db are the one-time manual setup above.

## 12. Manual jar deploy option
The fast path for the first cut — `github-actions/deploy-options.md` (Option A).

## 13. Verification checklist
Run every item in `verification/verification-checklist.md` and the commands in
`verification/post-deploy-smoke-test.md`. Do not cut over until all pass.

## 14. Rollback plan
`rollback/rollback-plan.md`. The OLD server is the fallback — keep it live and
unchanged until verification passes; don't change DNS as part of this task.

## 15. Known issues & non-blocking follow-ups
- Rotate the **owner password** and **Eskiz SMS password** (both surfaced earlier).
- Services run as **root** — move to a dedicated system user + `ProtectSystem` later.
- `ddl-auto=none` — can switch to `validate` after a staging schema check.
- License **H2 → PostgreSQL** migration is a later **P1** (not required now).
- nginx `/api/accounting/backfill` needs a long `proxy_read_timeout` (already in the
  nginx template) — a big backfill 504s at the default 60s but still commits.
- None of these block a normal deploy of the current test/pilot data.
