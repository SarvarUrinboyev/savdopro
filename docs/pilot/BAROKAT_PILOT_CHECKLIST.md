# Barokat Pilot Checklist

Operational checklist for running the first real-store pilot. Work top-to-bottom;
each box is a go/no-go item.

## Pre-pilot (the day before)

- [ ] Staging/pilot box provisioned and reachable over HTTPS (see [STAGING_SETUP.md](STAGING_SETUP.md)).
- [ ] `SAVDOPRO_JWT_SECRET` identical on backend + license; both started cleanly.
- [ ] `WEB_ALLOWED_ORIGINS` set to the pilot URL; CORS verified.
- [ ] Super-admin password rotated from the bootstrap default.
- [ ] Backend `mvnw test` green; license `mvnw test` green; frontend build + `npm audit --omit=dev` clean.
- [ ] Database backup job confirmed running (see Backup below) and one **restore** rehearsed.
- [ ] Owner + cashier accounts created for the real shop (or demo users if a dry-run).
- [ ] Receipt printer connected and a **test page** prints.
- [ ] Barcode scanner reads into the POS search box.

## User roles

| Role | Who | Can |
|---|---|---|
| ACCOUNT_OWNER | Shop owner | Everything for their account: all shops, reports, accounting, staff, settings |
| SHOP_USER (cashier) | Till staff | Sell, refund, view their shop; **cannot** see sibling shops or erase shift history |
| SUPER_ADMIN | Platform (you) | Manage accounts/subscriptions; never used for day-to-day selling |

Confirm the cashier account is SHOP_USER (not owner) so it is confined to one shop.

## Day-1 cashier flow (walk the cashier through it)

- [ ] Log in; POS opens with the search box focused.
- [ ] **Product import**: load the shop's catalogue via CSV/XLSX (Ombor → import) or add a few by hand.
- [ ] **Barcode scan**: scan a product → it lands in the cart.
- [ ] **Cash sale**: ring 2–3 items, take cash, print receipt.
- [ ] **Card sale**: same, paid by card.
- [ ] **Debt (QARZGA) sale**: pick a customer, sell on credit → customer's debt rises.
- [ ] **Refund**: refund one line of a past sale → stock returns, money-back recorded.

## Daily closing

- [ ] End of day: **close the shift** (or generate the daily report).
- [ ] Daily report figures match the till (cash counted vs expected).
- [ ] If Telegram is configured, the owner receives the end-of-day summary.

## Debt & repayment

- [ ] Open a customer with a debt → balance correct.
- [ ] Record a **repayment** → balance drops by the paid amount.
- [ ] (Optional) send a debt reminder to a customer.

## Stock operations

- [ ] **Stock count (stocktake)**: adjust a product's quantity; movement is logged.
- [ ] **Transfer** between two of the account's shops (if multi-shop): source drops, destination rises.
- [ ] Low-stock items are flagged on the dashboard/warehouse.

## Reports

- [ ] Dashboard non-empty and matches the day's activity.
- [ ] P&L for today: revenue, COGS, gross, expenses, net are sensible (see [ACCOUNTING_RULES.md](ACCOUNTING_RULES.md)).
- [ ] Sales history lists each sale with method and total.

## Backup

- [ ] Automated Postgres dump on a schedule (e.g. nightly `pg_dump`), stored **off the box**.
- [ ] Dumps are NOT in the git repo and contain no secrets in plaintext logs.
- [ ] Retention agreed (e.g. 7 daily + 4 weekly).

## Rollback

- [ ] Tagged release deployed; previous tag known.
- [ ] Rollback = redeploy previous image/jar + (only if a migration must be undone)
      restore the pre-deploy DB dump. Flyway migrations are forward-only; never
      hand-edit `flyway_schema_history`.
- [ ] Rehearsed: redeploy previous tag and confirm login + a sale still work.

## Incident plan

- [ ] **Who** to call (owner + you) and **how** (phone/Telegram).
- [ ] If the app is down: cashier falls back to manual receipts; POS offline-queue
      replays sales when back online (idempotent via client-ref).
- [ ] If data looks wrong: do **not** edit the DB directly; capture a screenshot +
      the sale id, run the **Ledger backfill** if the P&L looks unposted, and file it.
- [ ] If a security concern: rotate `SAVDOPRO_JWT_SECRET` (forces re-login) and the
      admin password; review `/api/audit` and login alerts.

## Sign-off

- [ ] Owner trained on: sell, refund, close day, read the daily report, check debt.
- [ ] Known limitations reviewed with the owner ([QA_ACCEPTANCE.md](QA_ACCEPTANCE.md)).
- [ ] Pilot start date + check-in cadence agreed.
