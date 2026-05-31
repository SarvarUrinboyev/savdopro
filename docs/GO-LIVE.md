# SavdoPRO — Go-Live Checklist

Practical steps to take the platform from "builds + tests green" to "serving
real merchants." Work top to bottom; nothing here needs code changes — only
configuration and the merchant credentials you obtain from the PSPs.

The code is ready: payment adapters (Click + Payme), subscription lifecycle,
metrics, backups and error-tracking are all implemented and unit-tested. What
remains is supplying secrets and pointing the PSPs at your webhook URLs.

---

## 1. Infrastructure

- [ ] A VPS (2 vCPU / 4 GB is plenty to start) with Docker + Docker Compose.
- [ ] A PostgreSQL instance (the bundled `docker-compose.yml` runs one; or use a managed DB).
- [ ] Two DNS records pointing at the VPS, e.g.
      `app.savdopro.uz` (merchant portal + data API) and
      `auth.savdopro.uz` (License Server).
- [ ] A reverse proxy (Caddy or nginx) terminating TLS for both hosts. Bind the
      app/license to `127.0.0.1` and let the proxy be the only public listener.

## 2. Secrets & configuration

Copy the templates and fill in real values — never commit the populated files:

- [ ] Root `.env`           ← `.env.example`            (docker-compose: DB, JWT, admin, billing, Sentry)
- [ ] `license-server/.env` ← `license-server/.env.example`
- [ ] `frontend/.env.production` ← `frontend/.env.example` (set `VITE_LICENSE_URL`, optionally `VITE_API_URL`)

Critical:

- [ ] **`SAVDOPRO_JWT_SECRET` is identical** for the backend and the License
      Server (64+ random chars: `openssl rand -base64 48`). A mismatch means
      login succeeds but every `/api/*` call returns 401.
- [ ] `SAVDOPRO_ADMIN_PASSWORD` is strong; change it again from the admin panel
      after first login.
- [ ] `frontend` web build sets `VITE_LICENSE_URL=https://auth.savdopro.uz`.
      (In prod this is pinned at build time, so the dev "localhost license URL"
      fallback never runs.)

## 3. Build & deploy

- [ ] `docker compose build` then `docker compose up -d` (or build the two jars
      and run them behind the proxy).
- [ ] Flyway migrations apply automatically on first boot (license: V1–V12).
- [ ] Build the web SPA with `VITE_TARGET=web` (the bundle is emitted into the
      backend's static resources, served same-origin).
- [ ] Verify health: `GET /actuator/health` on both services returns `UP`.

## 4. Payment providers (Click + Payme)

The adapters are implemented; you only register your webhook URLs in each
cabinet and paste the credentials into `license-server/.env`.

### Click (SHOP-API)
- [ ] In the Click merchant cabinet, set the **Prepare** and **Complete** URLs to:
      - Prepare:  `https://auth.savdopro.uz/api/billing/click/prepare`
      - Complete: `https://auth.savdopro.uz/api/billing/click/complete`
- [ ] Set `BILLING_CLICK_SERVICE_ID`, `BILLING_CLICK_MERCHANT_ID`,
      `BILLING_CLICK_SECRET_KEY`.
- [ ] Sandbox test (see §6).

### Payme (Paycom Merchant API)
- [ ] In the Payme cabinet, set the **Merchant endpoint** to:
      `https://auth.savdopro.uz/api/billing/payme`
- [ ] Register the account field (e.g. `order_id`) and set
      `BILLING_PAYME_ACCOUNT_FIELD` to match.
- [ ] Set `BILLING_PAYME_MERCHANT_ID`, `BILLING_PAYME_MERCHANT_KEY`.
- [ ] Sandbox test (see §6).

> The merchant portal's billing page already offers a Click/Payme picker that
> redirects to the provider's hosted checkout; once keys are set the round-trip
> works end to end (checkout → webhook → subscription extended).

## 5. Monitoring, backups, SMS

- [ ] **Backups**: schedule `ops/backup-postgres.sh` via cron (see its header).
      Verify a restore into a scratch DB at least once.
- [ ] **Metrics**: point Prometheus at `/actuator/prometheus` on each service
      (authenticated — scrape with a service bearer token, or expose it only on
      an internal management port).
- [ ] **Sentry**: set `SENTRY_DSN` to start receiving unhandled-exception reports.
- [ ] **SMS** (forgot-password / SMS login): a real provider (Eskiz / PlayMobile)
      is **not yet wired** — sending is currently a stub. Integrate the provider
      before advertising SMS recovery. (Tracked separately.)

## 6. PSP sandbox test plan

For each provider, before taking live traffic:

1. Create a trial account in the portal, open **Tarif va to'lov**, pick a plan
   and the provider → you're redirected to the PSP's (sandbox) checkout.
2. Complete a sandbox payment.
3. Confirm the provider calls your webhook:
   - **Click**: a `prepare` (action=0) then a `complete` (action=1); your
     responses carry `error: 0` and a `merchant_prepare_id` / `merchant_confirm_id`.
   - **Payme**: `CheckPerformTransaction` → `CreateTransaction` →
     `PerformTransaction` (JSON-RPC), each returning a `result`.
4. Verify the effect: the `payments` row flips `PENDING → PAID`, and the
   account's `subscription_expires` is extended (and `plan` upgraded).
5. Negative checks: a wrong signature is rejected (Click `error: -1`; Payme
   `-32504`), and a wrong amount is rejected (Click `-2`; Payme `-31001`).

## 7. Post-deploy smoke check

- [ ] Sign up a fresh merchant → 14-day trial, onboarding checklist appears.
- [ ] Add a product, make a sale, add a customer → checklist ticks to 3/3.
- [ ] Let a subscription lapse (or set `subscription_expires` in the past) →
      writes are blocked (read-only) and the billing banner appears.
- [ ] Pay via Click and via Payme (sandbox) → subscription extended.
- [ ] Super-admin can grant/extend a subscription manually.
- [ ] `GET /actuator/health` = `UP`; a test error shows up in Sentry; a backup
      file lands in the backup directory.
