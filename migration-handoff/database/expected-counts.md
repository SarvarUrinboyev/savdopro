# Expected Data Counts (must match after restore)

Snapshot verified on the OLD server 2026-07-01 17:14 (PostgreSQL `barakat`).

## Core
| Item | Expected |
|---|---|
| accounts | **13** |
| shops | **15** |
| total products | **6618** |
| target account_id | **1003** |
| target shop_id | **6** |
| shop 6 products | **6598** |

## Accounting (shop 6 ledger, after backfill)
| Item | Expected |
|---|---|
| shop 6 chart of accounts (gl_account) | **18** |
| inventory account `1300` | present |
| opening-equity account `3900` | present |
| stock movements (shop 6) | **2780** |
| gl_journal_entry (shop 6) | **2780** |
| gl_journal_line (shop 6) | **5560** |
| inventory value (debit 1300 = credit 3900) | **262,557,334.87** |
| trial balance balanced | **true** |
| balance sheet balanced | **true** |

## Quick check query
```sql
SELECT 'accounts',count(*) FROM accounts
UNION ALL SELECT 'shops',count(*) FROM shops
UNION ALL SELECT 'products',count(*) FROM products
UNION ALL SELECT 'shop6_products',count(*) FROM products WHERE shop_id=6
UNION ALL SELECT 'gl_account_shop6',count(*) FROM gl_account WHERE shop_id=6
UNION ALL SELECT 'gl_entries_shop6',count(*) FROM gl_journal_entry WHERE shop_id=6
UNION ALL SELECT 'gl_lines_shop6',count(*) FROM gl_journal_line WHERE shop_id=6;
```
If any count is lower, the restore is incomplete — **STOP** and re-restore.
Do **not** re-run the Excel import or the ledger backfill to "fix" counts.
