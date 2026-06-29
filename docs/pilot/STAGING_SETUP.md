# Staging Setup

How to stand up a SavdoPRO staging environment, seed it, and verify it is wired
correctly and **not** pointing at production data. Three services: PostgreSQL,
the **license-server** (auth/billing, port 9090) and the **backend** (data API,
port 8086) which also serves the built frontend.

## 1. Required environment variables

Copy `.env.example` → `.env` (gitignored) and set real values. Used by
`docker-compose.yml`.

| Var | Service | Notes |
|---|---|---|
| `DB_USER` / `DB_PASSWORD` / `DB_NAME` | db | Postgres credentials. **Use a staging-only password.** |
| `DB_URL` | backend | `jdbc:postgresql://<host>:5432/<db>` — point at the **staging** DB, never prod |
| `SAVDOPRO_JWT_SECRET` | backend + license | **Identical** 64+ char random string on both (`openssl rand -base64 48`). A mismatch = login works but every `/api` call 401s |
| `SAVDOPRO_ADMIN_USER` / `SAVDOPRO_ADMIN_PASSWORD` / `SAVDOPRO_ADMIN_NAME` | license | Super-admin bootstrap. Password must pass the strength rule (8+, letter+digit, not a known weak default) |
| `WEB_ALLOWED_ORIGINS` | backend (prod) | Exact origin(s), comma-separated, e.g. `https://staging.example.com`. **Backend refuses to start if unset under prod.** No wildcard |
| `TRUST_PROXY_HEADERS` | backend | `true` behind nginx so client IPs are read from `X-Forwarded-For` |
| `TRUSTED_PROXY_CIDRS` | backend | CIDRs allowed to set forwarded headers (default `127.0.0.1/32,::1/128`) |
| `ALLOW_DEMO_SEED` | backend + license | `true` on staging to seed demo data (see §3). **Leave unset/false in production** |
| `DEMO_SEED_PASSWORD` | license | Strong password for the demo login users (only used when seeding) |
| `SPRING_PROFILES_ACTIVE` | backend | `prod` for Postgres + locked-down config (set by docker-compose) |
| Telegram (optional) | backend | `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` — blank disables the daily report |

## 2. Boot the services

### Option A — Docker (prod-like, recommended for staging)
```bash
cp .env.example .env        # then edit .env with staging values
# add the demo flags for staging:
echo 'ALLOW_DEMO_SEED=true' >> .env
echo "DEMO_SEED_PASSWORD=$(openssl rand -base64 12)0a" >> .env   # ensure letter+digit
docker compose up -d --build
docker compose ps           # db, license-server (9090), backend (8086) healthy
```

### Option B — Local JVM (quick dev/staging)
```bash
# License server (auth) on :9090
ALLOW_DEMO_SEED=true DEMO_SEED_PASSWORD='Demo2026pilot' \
  SAVDOPRO_JWT_SECRET='<64+ chars>' SAVDOPRO_ADMIN_PASSWORD='<strong>' \
  ./mvnw -pl license-server spring-boot:run

# Backend (data) on :8086
ALLOW_DEMO_SEED=true SAVDOPRO_JWT_SECRET='<same 64+ chars>' \
  ./mvnw -pl backend spring-boot:run

# Frontend (dev server) — or use the build served by the backend
cd frontend && npm install && npm run dev
```

## 3. Run the seed safely

The seed is **guarded** and idempotent (full detail in [SEED_DATA.md](SEED_DATA.md)).
With `ALLOW_DEMO_SEED=true` set, it runs once on startup:
- backend logs `Demo seed complete. Accounts A=90001 B=90002 …`
- license logs `Seeded N demo login user(s): demo_owner / demo_kassir / demo_owner_b`

Re-running is a no-op. To re-seed a **dev** box for a fresh "today", stop it and
delete the H2 files — **never** on staging/prod Postgres.

## 4. Verification

**Services up**
```bash
curl -fsS http://localhost:8086/api/health      # backend
curl -fsS http://localhost:9090/api/health       # license (if exposed)
```

**Not using production DB** — confirm the JDBC URL points at staging:
```bash
docker compose exec backend printenv DB_URL       # -> staging host/db, NOT prod
docker compose exec db psql -U "$DB_USER" -l       # the staging database is listed
```

**CORS / origins** — a request from a disallowed origin must be rejected:
```bash
curl -si http://localhost:8086/api/health -H 'Origin: https://evil.example' | grep -i access-control-allow-origin
# should NOT echo the evil origin; only WEB_ALLOWED_ORIGINS values are allowed
```

**Proxy / client IP** — behind nginx, `TRUST_PROXY_HEADERS=true` and
`TRUSTED_PROXY_CIDRS` includes the proxy; verify the app logs real client IPs
(not the proxy's) on login attempts.

**Login + seeded data** (the 5-minute smoke):
1. Open the app, log in as `demo_owner` (password = `DEMO_SEED_PASSWORD`).
2. Dashboard shows today's figures; Warehouse lists 20 products; POS finds a
   product by name and by barcode `4780001000017`.
3. Ring a cash sale → stock drops, payment recorded, P&L revenue rises.

**Build & tests**
```bash
./mvnw -pl backend test            # 250+ green
./mvnw -pl license-server test     # 146 green
cd frontend && npm run build && npm audit --omit=dev   # build ok, 0 vulns
```

## 5. Production guard reminders

- `ALLOW_DEMO_SEED` MUST be unset/false in production (also hard-disabled under
  the `prod`/`test` profiles in code).
- Never commit `.env`, dumps, logs, keys, or certificates (all gitignored).
- Rotate `SAVDOPRO_ADMIN_PASSWORD` after first login; the app warns if it is weak.
