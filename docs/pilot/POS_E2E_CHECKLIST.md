# POS End-to-End QA Checklist

Manual acceptance script for the cashier → stock → payment → report → accounting
flow. Run it on a dev/staging box seeded with demo data (see
[SEED_DATA.md](SEED_DATA.md)). Log in as `demo_kassir` (cashier) or `demo_owner`.

The automated proof of this flow lives in
`backend/.../service/PosEndToEndIT.java` (and `AccountingFlowTest`,
`DemoDataSeederIT`); this checklist is the human walk-through for a pilot demo.

## A. Sell

| # | Step | Expected |
|---|------|----------|
| 1 | Open **Kassa (POS)** | Product search box is focused; checkout disabled with an empty cart |
| 2 | Search a product by **name** (e.g. "Coca") | Matches appear; Enter / click adds to cart |
| 3 | Search by **barcode/SKU** (scan or type `4780001000017`) | Exact product added to cart |
| 4 | Change a line **quantity** | Line total = unit price × qty |
| 5 | Apply a **discount** (line or whole-sale) | Cart total = Σ(price×qty) − discount |
| 6 | Pick a **customer** (optional) | Customer name shows on the receipt |
| 7 | Complete a **cash (NAQD)** sale | Receipt prints; sale appears in **Savdolar tarixi** |
| 8 | Complete a **card (KARTA)** sale | Booked under card; separate from cash |
| 9 | Complete a **transfer (TRANSFER)** sale | Booked under bank/transfer |
| 10 | Complete an **on-credit (QARZGA)** sale with a customer | No cash booked; the customer's debt rises by the total |

## B. Verify state changed

| # | Check | Expected |
|---|-------|----------|
| 11 | **Ombor (warehouse)** stock of a sold product | Decreased by exactly the sold quantity |
| 12 | **To'lovlar / Kassa** | Cash/card/transfer amounts increased by the paid totals |
| 13 | **Savdolar tarixi / reports** | Each sale is listed with its method and total |
| 14 | **Mijozlar** → the credit customer | Balance (qarz) increased by the QARZGA sale total |

## C. Money back & debt down

| # | Step | Expected |
|---|------|----------|
| 15 | **Refund** part of a sale (e.g. 2 of 5 units) | Stock goes back up by 2; a refund (OUTGOING) is recorded; sale shows "qaytarilgan" amount |
| 16 | Record a **debt repayment** (PAYMENT) for the credit customer | Customer balance drops by the paid amount |

## D. Accounting reconciles

| # | Check | Expected |
|---|-------|----------|
| 17 | **Hisobotlar → Foyda-zarar (P&L)** for today | Revenue = completed sale subtotals − discounts; COGS = Σ(qty × cost frozen at sale time); Gross = Revenue − COGS |
| 18 | Edit a product's **cost** after a sale, re-open the P&L | The earlier sale's COGS is **unchanged** (cost snapshot, V37) |
| 19 | **Trial balance / balance sheet** | Balanced (debits = credits) |
| 20 | **Dashboard** | Today's figures are non-zero and match the sales just rung up |

## Notes / known limitations

- A QARZGA sale **with no customer** (walk-in credit) books a receivable in the
  ledger but cannot raise a per-customer balance — pick a customer for credit sales.
- The ledger treats sales and expenses on a single canonical scale; enter expenses
  in the same currency as product prices. See [ACCOUNTING_RULES.md](ACCOUNTING_RULES.md).
- There is no formal Z/X-report; closing a shift sends an end-of-day summary
  (Telegram) instead.
