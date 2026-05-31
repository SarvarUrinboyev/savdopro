# SavdoPRO — Project Status

A living snapshot of where the platform stands. Pairs with `GO-LIVE.md`
(deployment) and `DEFERRED-FEATURES.md` (backlog).

## What it is

A multi-tenant POS / shop-management SaaS for Uzbekistan, delivered as a hosted
web portal and a desktop (Electron) app from one codebase.

| Service | Port | Responsibility |
|---------|------|----------------|
| **License Server** | 9090 | Auth, accounts, **subscriptions/billing**, super-admin, JWT minting, SMS OTP, Telegram OAuth, white-label |
| **Backend** (POS) | 8086 | Sales, products, customers, debt, reports, AI — multi-tenant data plane |
| **Frontend** | — | React/Vite SPA (web portal + desktop) |

The two services share a JWT signing secret: the License Server **mints** tokens
(role, accountId, perms, `subExp`, `blk`, `maxShops`); the backend **validates**
them and enforces tenant isolation (Hibernate `@Filter`) and `RESOURCE:ACTION`
permissions per endpoint.

## Scale

- **234 automated tests**, all green (backend 125, license 109).
- ~212 backend + 54 license Java sources; 78 frontend modules; 12 Flyway migrations.
- 9 modules: `backend`, `license-server`, `frontend`, `electron`, `mobile`,
  `docker`, `ops`, `docs`, `mock-backend`.

## Capabilities (done)

**Foundation** — tenant isolation (fail-closed), granular authorization,
secured WebSocket, validation, pagination, PostgreSQL support, CI (GitHub
Actions), Docker.

**AI assistant** — tenant-scoped tool-calling (sales, top products, hourly,
finance, low stock) with conversation memory.

**Monetization (full lifecycle)**
- Self-service signup → 14-day trial.
- Subscription plans (TRIAL/BASIC/STANDARD/PRO) with per-account user & shop limits, enforced server-side.
- Read-only enforcement on a lapsed subscription (writes blocked, reads + billing open), surfaced by a dashboard banner.
- In-app billing page: status, plan picker, payment history.
- **Payment adapters**: Click (SHOP-API, MD5-signed Prepare/Complete) and Payme
  (Paycom Merchant-API, JSON-RPC state machine) behind a provider abstraction;
  webhook → confirm → subscription extended; all signature-verified, idempotent, unit-tested.
- Super-admin subscription management (plan change + manual grant).

**Onboarding** — public marketing landing page; first-run dashboard checklist
(add product → make sale → add customer) that ticks itself off from live data.

**Production hardening** — Prometheus metrics (secured), PostgreSQL backup
script, Sentry error-tracking scaffolding (env-gated).

## Pending (needs the operator's credentials)

- **Payme/Click go-live**: paste merchant keys into `*.env`, register webhook
  URLs, run the sandbox test plan (`GO-LIVE.md` §4/§6). Code is complete.
- **Real SMS provider** (Eskiz/PlayMobile): sending is currently a stub —
  integrate before advertising SMS recovery / SMS login.
- **Sentry DSN**: set `SENTRY_DSN` to activate error reporting.

## Branch

All of the above lives on **`feat/web-merchant-portal`** (pushed to origin).
See `GO-LIVE.md` to deploy.
