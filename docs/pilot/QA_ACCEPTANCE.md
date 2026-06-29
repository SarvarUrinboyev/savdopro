# QA Acceptance

What must be true before SavdoPRO is used in a real store, the automated/manual
evidence, and the known limitations to disclose to the pilot owner.

## Must pass before real use

| Gate | Command / check | Status |
|---|---|---|
| Backend tests | `./mvnw -pl backend test` | ✅ 250 passing |
| License tests | `./mvnw -pl license-server test` | ✅ 146 passing |
| Frontend build | `cd frontend && npm run build` | ✅ builds |
| Frontend prod deps | `npm audit --omit=dev` | ✅ 0 vulnerabilities |
| Security blockers | go-live fixes commit `c6d93a8` intact (not weakened) | ✅ |
| Seed is production-safe | `DemoDataSeederGuardTest` | ✅ OFF under prod/test |
| POS end-to-end | `PosEndToEndIT` | ✅ stock/payment/debt/refund/COGS |
| Accounting reconciles | `AccountingFlowTest`, `AccountingReconciliationIT` | ✅ |
| Tenant isolation | `TenantIsolationEndpointIT`, `TenantIsolationIntegrationTest` | ✅ A↔B 403/404 |

## Manual acceptance criteria

- A founder can demo, in ~5 minutes, on seeded staging: login → dashboard →
  warehouse → POS sale → payment/cashbox → customer debt → P&L → report →
  tenant-isolation concept. (See [POS_E2E_CHECKLIST.md](POS_E2E_CHECKLIST.md).)
- Stock decreases on sale and increases on refund.
- Cash/card/transfer/debt are distinguishable; a debt sale raises customer debt
  and a repayment lowers it.
- P&L revenue/COGS/gross/net are non-zero, explainable, and unchanged by a later
  product-cost edit (cost snapshot).
- Account A cannot see Account B's data (403/404), including by editing `X-Shop-Id`.
- Empty pages (fresh account) show a clear next action.

## Known limitations (disclose to the owner)

1. **Single-scale accounting (P1).** The ledger does not normalise currencies:
   sales/COGS post unconverted while expenses/payments convert by their currency
   field. Enter expenses on the same scale as product prices. Full multi-currency
   normalisation is a tracked follow-up. See [ACCOUNTING_RULES.md](ACCOUNTING_RULES.md).
2. **Receivable duality (P2).** An on-credit sale's debt is tracked in both the GL
   (account 1400) and the per-customer ledger; they are not auto-reconciled.
3. **First-sale ledger seeding (P2).** A brand-new shop's first ledger post relies
   on the after-commit hook; if ever skipped, the **Ledger backfill** action re-posts
   all history idempotently and recovers the P&L.
4. **Demo roles (P2).** The demo login seed creates owner + cashier (+ B owner) only;
   a fully-permissioned demo manager/accountant is a follow-up (real accounts can
   still be created with any role/permissions).
5. **No frontend E2E harness (P3).** Browser flows are covered by the manual
   POS checklist, not an automated Playwright/Cypress suite.
6. **No formal Z/X report (P3).** Shift close sends an end-of-day summary instead.
7. **Frontend bundle > 500 kB (P3).** Works fine; code-splitting is a perf follow-up.

## Remaining issues by priority

- **P0 (blocker):** none open. Go-live security blockers were closed in `c6d93a8`
  and are not weakened by this work.
- **P1 (fix before scaling beyond the pilot):**
  - Single canonical reporting currency / per-sale FX snapshot so mixed-currency
    shops reconcile without operator care.
- **P2 (fix during the pilot):**
  - Reconcile GL receivable with the per-customer ledger (or pick one source of truth).
  - Auto-seed the GL chart when a shop is created so a first sale can never read as
    zero P&L (today: backfill recovers it).
  - Seed/support a demo manager + accountant role for fuller demos.
- **P3 (nice-to-have):**
  - Frontend E2E automation; code-split the bundle; formal Z/X report.

## Evidence to capture for the pilot file

- CI/test output (backend, license, frontend build + audit).
- Screenshots: seeded dashboard, a POS sale, P&L for the day, a 403 on a tampered
  `X-Shop-Id`.
- The staging `.env` var **names** present (never the values).
