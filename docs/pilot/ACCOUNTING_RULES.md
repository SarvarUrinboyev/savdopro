# Accounting Rules

How SavdoPRO turns POS activity into numbers, and the rules a store owner can
rely on. The double-entry engine lives in `LedgerPostingService`; reports are
computed by `FinancialStatementService` (ledger-based) and the operational
`ReportService` / `ManagementService` (table-based). All are tenant-scoped.

Automated proof: `AccountingFlowTest`, `AccountingReconciliationIT`,
`PosEndToEndIT`, `DemoDataSeederIT`.

## Chart of accounts (per shop)

Seeded on first use (`ChartOfAccountsService`). Key codes:

| Code | Account | Used by |
|---|---|---|
| 1100 | Cash (Kassa) | cash sales, cash expenses |
| 1200 | Bank / terminal | card & transfer sales |
| 1300 | Inventory | COGS / stock |
| 1400 | Receivable (mijozlar qarzi) | on-credit (QARZGA) sales |
| 4100 / 4200 / 4300 | Sales / Discount / Returns | revenue side |
| 5100 | COGS (tannarx) | cost of goods sold |
| 6100–6900 | Salary / Tax / Rent / Other expense | expenses |

## Formulas

| Figure | Definition |
|---|---|
| **Gross revenue** | Σ completed sale subtotals − discounts (`SALES` credit − `SALES_DISCOUNT` debit) − returns |
| **COGS** | Σ (quantity × **cost frozen on the sale line at sale time**) — see snapshot below |
| **Gross profit** | Gross revenue − COGS |
| **Expenses** | Posted expenses for the period (accounts 6xxx) |
| **Net profit** | Gross profit − Expenses |
| **Cash flow** | Actual money in/out of Cash + Bank (not accruals) |
| **Receivable** | Σ unpaid on-credit balances |

## Sale postings (what a checkout books)

- **Cash (NAQD/KASSA)** → Dr **Cash** + Dr COGS, Cr **Sales** + Cr Inventory.
- **Card / Transfer (KARTA/P2P/TRANSFER)** → Dr **Bank** (instead of Cash).
- **On-credit (QARZGA)** → Dr **Receivable** (no cash in) + the same revenue/COGS.
  A QARZGA sale to a known customer **also** raises that customer's debt ledger
  (a GOODS line), so it shows on the Mijozlar page.
- **Refund** → Dr Sales-Returns + Dr Inventory, Cr Cash/Bank/Receivable + Cr COGS
  (the COGS reversal uses the **same frozen cost** the sale booked).

So a debt sale increases **revenue and receivable** but **not cash**; cash rises
only when the customer repays. (`AccountingReconciliationIT` locks this in.)

## COGS cost snapshot (V37)

Each sale line freezes the product's cost at checkout (`sale_items.cost_at_sale_uzs`).
COGS in the ledger, the end-of-day report and the management page all read this
snapshot. **Editing a product's cost later does not change the profit of past
sales.** Legacy lines without a snapshot fall back to the product's current cost.

## Debt repayment

Recording a customer **PAYMENT** lowers their balance (balance = ΣGOODS − ΣPAYMENT).
It does not by itself post a cash-journal row — cash collection is recorded
separately in the Payments / Kassa module. Keep the two in step when collecting debt.

## UZS / USD handling — IMPORTANT (P1 follow-up)

The ledger is single-scale. **Sales and COGS post unconverted** (the sale's
own units), while **expenses, payments and management costs are converted by
their `currency` field** (UZS → USD at the CBU rate, USD passes through).

- If your products are priced in **USD** (e.g. electronics), enter expenses in
  **USD** too — everything is then on one scale and the P&L is exact.
- If your products are priced in **UZS**, enter expenses with the currency that
  keeps them on the **same scale as your product prices** (i.e. do not let a
  UZS expense be divided by the FX rate against UZS-magnitude revenue).

Why it is not "just converted everywhere": converting the sale side would make
every P&L depend on the live daily FX rate, i.e. the same historical sale would
report a different profit each day. Sales are therefore kept rate-stable.

**Follow-up (P1):** introduce a single explicit reporting currency (or store a
per-sale FX snapshot) so mixed-currency shops reconcile automatically without the
operator having to think about expense currency. Until then the demo seed and
tests keep everything on one scale.

## Other known limitations

- **Receivable duality (P2):** an on-credit sale's receivable lives both in the
  GL (`1400`) and in the per-customer ledger (`customer_transactions`). They
  represent the same debt in two subsystems and are not auto-reconciled.
- **First-sale ledger seeding (P2):** the ledger posts after the sale commits.
  If a brand-new shop's first posting is ever skipped, the P&L can read zero
  while sales exist; the **Ledger backfill** action re-posts all history
  idempotently and recovers it. The demo seed uses backfill directly.
- **No Z/X report:** shift close sends an end-of-day summary instead.
