# Demo / Staging Seed Data

Guarded, idempotent demo data so SavdoPRO can be exercised end-to-end (dashboard,
warehouse, POS, customers, debt, accounting, reports) and so tenant A/B isolation
can be demonstrated on a live dev/staging box.

> **Never runs in production.** Seeding is OFF by default and is hard-disabled
> under the `prod` and `test` Spring profiles. It writes only to a reserved demo
> id band, so it can never touch real tenants.

## What gets created

Two services seed compatible data; the only link they need is the `accountId`
(90001 / 90002), which the JWT carries and the backend resolves to a shop.

### Backend (`DemoDataSeeder`)
| Tenant | Id | Shops |
|---|---|---|
| DEMO — Barokat Savdo (A) | account `90001` | `90101` Markaziy (main), `90102` Filial |
| DEMO — Raqobatchi Do'kon (B) | account `90002` | `90201` (main) |

- **Shop A main (`90101`)**: 20 realistic UZS products across 2 categories; 3
  customers (normal / debtor / partial-payment); 4 POS sales through the real
  checkout engine — **cash, card, bank-transfer and on-credit (QARZGA)**; 2
  daily expenses. All of today's date.
- **Shop A branch (`90102`)**: a few products incl. one intentionally low on
  stock, plus a stock **transfer** received from the main shop.
- **Shop B (`90201`)**: 5 deliberately *distinct* products + 1 customer + 1 sale,
  so cross-tenant leakage between A and B is obvious.

The sales/expenses are posted to the double-entry ledger synchronously via the
existing `LedgerBackfillService` (idempotent; POS payment rows excluded so the
sale is never double-counted). The resulting shop-A P&L is exact and explainable:

| Line | Amount (canonical scale) |
|---|---|
| Revenue | 331 500 |
| COGS | 261 300 |
| **Gross profit** | **70 200** |
| Expenses | 45 000 |
| **Net profit** | **25 200** |

Customer debt on shop A = **330 000** (debtor 250 000 + partial 80 000).

### License-server (`DemoUserSeeder`)
Demo login users on accounts 90001 / 90002 (password = the `DEMO_SEED_PASSWORD`
you provide — never hardcoded):

| Username | Role | Account |
|---|---|---|
| `demo_owner` | ACCOUNT_OWNER | 90001 |
| `demo_kassir` | SHOP_USER | 90001 |
| `demo_owner_b` | ACCOUNT_OWNER | 90002 |

## How to run it (dev / staging only)

Set the flag (and, for login users, a strong password) and start the services:

```bash
# Backend (port 8086) — seeds tenants, products, sales, expenses, debt
ALLOW_DEMO_SEED=true ./mvnw -pl backend spring-boot:run

# License server (port 9090) — seeds the demo login users
ALLOW_DEMO_SEED=true DEMO_SEED_PASSWORD='Demo2026pilot' ./mvnw -pl license-server spring-boot:run
```

On Docker/staging set them as environment variables on the two services.
`DEMO_SEED_PASSWORD` must be ≥8 chars with at least one letter and one digit, or
the user seed is skipped with a WARN (the data seed still runs).

## Idempotency & safety

- **Idempotent**: if demo account `90001` already exists the whole run is a no-op,
  so a restart never duplicates rows. Demo login users are created only if the
  username is free.
- **Production-safe**: disabled under `prod`/`test` profiles regardless of the flag.
- **Fresh demo day**: dashboard/management widgets are "today"-scoped, so seed on
  the morning you demo. To reset a *dev* box, stop the backend and delete the H2
  files (`barakat.mv.db`, `barakat.trace.db`) — **never** do this on a shared/staging DB.

## Verifying without a running stack

Automated proof lives in:
- `backend/.../service/demo/DemoDataSeederIT.java` — drives the real seed on an
  isolated in-memory DB and asserts the exact P&L, catalogue size, customer debt
  and idempotency.
- `backend/.../service/demo/DemoDataSeederGuardTest.java` — proves the seed is OFF
  by default and never runs under `prod`/`test`.
