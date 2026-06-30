# Warehouse Excel → production import runbook

**Safety:** the importer refuses to run without `ALLOW_WAREHOUSE_IMPORT=true`, resolves exactly one shop by name (aborts if absent), upserts by barcode, and **never deletes**. Do the steps in order. **STOP** on any failure.

Verified on staging (clean H2, shop `Asosiy do'kon`): 6 598 valid rows → created 6 598 / skipped 0; re-run → 0 created / 6 598 updated; 0 duplicate barcodes; 311 duplicate-name groups; 8 negative-stock rows corrected to 0; 142 fractional rounded; categories assigned & preserved.

## Prerequisites
- Backend release (barcode-identity uniqueness) **deployed and live** — the importer relies on it.
- `imports/Товары.xlsx` present (gitignored; never committed).
- Python 3 with `openpyxl` + `requests`.
- A production **ACCOUNT_OWNER** login for the target account.

## 0 — Backup (mandatory, before any write)
```bash
# Detect DB:  systemctl status barakat* ;  find /opt -name '*.mv.db' 2>/dev/null
# PostgreSQL:
pg_dump -Fc -d <dbname> > ~/barakat_pre_excel_import_$(date +%Y%m%d_%H%M).dump
ls -lh ~/barakat_pre_excel_import_*.dump          # must be non-zero
# H2 file DB:  quiesce, then  cp ./barakat.mv.db barakat_pre_excel_import_<ts>.mv.db
```
Record the restore command. **If the backup fails or is 0 bytes → STOP.**

## 1 — Preview (read-only; no writes)
Put credentials in env on the machine that runs the importer — **never in commits or chat**.
```bash
export ALLOW_WAREHOUSE_IMPORT=true \
  WAREHOUSE_IMPORT_FILE=imports/Товары.xlsx \
  WAREHOUSE_IMPORT_SHOP_NAME="Asosiy do'kon" \
  WAREHOUSE_IMPORT_USER='<owner_login>' WAREHOUSE_IMPORT_PASSWORD='<owner_pw>' \
  WAREHOUSE_IMPORT_API_URL=https://167-172-164-214.nip.io \
  WAREHOUSE_IMPORT_LICENSE_URL=https://167-172-164-214.nip.io \
  WAREHOUSE_IMPORT_RATE_PER_MIN=540 WAREHOUSE_IMPORT_PREVIEW=true
PYTHONUTF8=1 python imports/warehouse_import.py
```
Confirm: shop resolved exactly to **`Asosiy do'kon`** (note its id; confirm it's the intended shop), `valid=6598`, `negative_stock_corrected_to_zero=8`, projected create/update sane.
**STOP if** the shop is not found (the importer prints the available shop names — review them) **or** counts differ from the expected.

## 2 — Import
Re-run step 1 **without** `WAREHOUSE_IMPORT_PREVIEW`. Expect ~13 min; `created=6598`, `skipped_errors=0`, `negative_stock_corrected_to_zero=8`.

## 3 — Idempotency
Run the same command once more → `created=0`, `updated=6598`, 0 new products.

## 4 — Verify
- Warehouse count ≈ **6 598** in `Asosiy do'kon`.
- Search by **code** (e.g. `16993`) → one product. Search by **name** → matches.
- POS search works; duplicate-name group **`Импра коробка пакет` → 6 separate products/cards** with per-row stock/price.
- Spot-check 30 products against the Excel; the 8 negative-stock rows show **0**.
- Other shops' product counts **unchanged**.

## Rollback
Restore the §0 backup (`pg_restore -c -d <db> <dump>`, or swap the `.mv.db`). The importer adds only to the target shop, so no other shop is affected.

## Tunables
- `WAREHOUSE_IMPORT_RATE_PER_MIN` — keep ≤ the server limit (default 600/min). `540` is safe; the importer also retries HTTP 429.
- `WAREHOUSE_IMPORT_DEFAULT_UNIT` — defaults to `dona`.
- `WAREHOUSE_IMPORT_LIMIT` — import only the first N valid rows (testing only).
