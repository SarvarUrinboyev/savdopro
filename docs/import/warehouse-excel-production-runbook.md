# Warehouse Excel → production import runbook

**Safety:** the importer refuses to run without `ALLOW_WAREHOUSE_IMPORT=true`. Against a
**non-local (production) API it now refuses name-based targeting** and requires an
**exact shop id + account id**, because every account's main shop is literally named
`Asosiy do'kon` (production currently has **15** shops with that identical name). It then
verifies the authenticated token owns that shop, sets `X-Shop-Id` to the exact id for
every call, snapshots per-shop product counts **before/after**, upserts by barcode, and
**never deletes**. Do the steps in order. **STOP** on any failure.

Excel verified (offline dry-run, `Товары.xlsx`): **6 598 rows → 6 598 importable**
(6 590 clean + 8 negative-stock rows corrected to 0), **0 duplicate barcodes**,
142 fractional qty rounded, 5 categories.

## 0 — Confirm the target shop by ID (READ-ONLY, mandatory)

> **CONFIRMED TARGET (2026-06-30, founder-approved):**
> `WAREHOUSE_IMPORT_SHOP_ID=6`, `WAREHOUSE_IMPORT_ACCOUNT_ID=1003`
> (account login `urinboyevsarvar97@gmail.com`, its sole shop, currently 0 products).
> **Auth = account 1003's OWNER login. Do NOT use super-admin** — super-admin (account 1)
> is rejected cross-account by the backend and must not be the import identity. Super-admin
> may *monitor* via this read-only query / the admin panel. **Do not import into account 1.**

Name is **not** a safe key. Re-verify the exact target with a read-only query before each run
(counts move). From the droplet (system Postgres, peer auth — no password needed):

```bash
sudo -u postgres psql -d barakat -A -F'|' -X <<'SQL'
SET default_transaction_read_only = on;
SELECT s.id AS shop_id, s.account_id, a.name AS account_login,
       (SELECT count(*) FROM products p WHERE p.shop_id=s.id) AS products
FROM shops s JOIN accounts a ON a.id=s.account_id
ORDER BY s.id;
SQL
```

Pick the row that is **your** shop. Record `WAREHOUSE_IMPORT_SHOP_ID` (= `shop_id`) and
`WAREHOUSE_IMPORT_ACCOUNT_ID` (= `account_id`). **If more than one row could be yours,
STOP** and get the founder to confirm which shop_id is the real main shop.

**Auth method (decided by where the target lives):**
- Target shop is in the **super-admin's own account (account 1)** → you may import as the
  super-admin: `WAREHOUSE_IMPORT_SUPERADMIN=true` with that account's login.
- Target shop is in a **separate owner account** → import as **that account's
  ACCOUNT_OWNER**. Super-admin **cannot** write into another account's shop (the backend
  rejects a cross-account `X-Shop-Id` with 403 — this is by design, do not work around it).
  If that account signed up via Google/Telegram and has no password, set one first via the
  super-admin panel (Reset password), then use it here.

## 1 — Backup (mandatory, before any write)
```bash
pg_dump -Fc -d barakat > ~/barakat_pre_excel_import_$(date +%Y%m%d_%H%M).dump
ls -lh ~/barakat_pre_excel_import_*.dump          # must be non-zero
```
Record the restore command. **If the backup fails or is 0 bytes → STOP.**

## 2 — Preview (read-only; no writes)
Put credentials in env on the machine that runs the importer — **never in commits or chat**.
```bash
export ALLOW_WAREHOUSE_IMPORT=true \
  WAREHOUSE_IMPORT_FILE=imports/Товары.xlsx \
  WAREHOUSE_IMPORT_TARGET_MODE=shop_id \
  WAREHOUSE_IMPORT_SHOP_ID=6 \
  WAREHOUSE_IMPORT_ACCOUNT_ID=1003 \
  WAREHOUSE_IMPORT_USER='<account 1003 owner login>' WAREHOUSE_IMPORT_PASSWORD='<pw>' \
  WAREHOUSE_IMPORT_API_URL=https://167-172-164-214.nip.io \
  WAREHOUSE_IMPORT_LICENSE_URL=https://167-172-164-214.nip.io \
  WAREHOUSE_IMPORT_RATE_PER_MIN=540 WAREHOUSE_IMPORT_PREVIEW=true
# Do NOT set WAREHOUSE_IMPORT_SUPERADMIN — the target is in account 1003, not the
# super-admin account, so the import MUST authenticate as the 1003 owner.
PYTHONUTF8=1 python imports/warehouse_import.py
```
> **Password prerequisite:** account 1003 (`urinboyevsarvar97@gmail.com`) is a Google
> signup and may have no usable password for the username/password login the importer uses.
> If login fails, set a password for that account from the super-admin panel (Reset
> password) first, then use it here. Never put the password in a commit or in chat.
Confirm: `Shop OK: id=<target>`, the printed `Baseline product counts` look right,
`valid=6598`, `negative_stock_corrected_to_zero=8`, projected create/update sane.
**STOP if** the importer aborts (it prints exactly why: wrong account, shop not owned, or
non-target drift) **or** counts differ from the expected.

## 3 — Import
Re-run step 2 **without** `WAREHOUSE_IMPORT_PREVIEW`. Expect ~13 min; `created=6598`,
`skipped_errors=0`. The run ends with `shop_count_guard.non_target_drift = []`; if it is
non-empty the importer **STOPs** and you must investigate before doing anything else.

## 4 — Idempotency
Run the same command once more → `created=0`, `updated=6598`, 0 new products.

## 5 — Verify
- Warehouse count ≈ **6 598** in the target shop (and `result.json`
  `shop_count_guard.after` shows only the target grew).
- Read-only DB re-check: re-run the §0 query → only the target shop's `products` rose by
  ~6 598; **every other shop unchanged**.
- Search by **code** (e.g. `16993`) → one product. POS search works.
- Spot-check 30 products against the Excel; the 8 negative-stock rows show **0**.

## Rollback
Restore the §1 backup (`pg_restore -c -d barakat <dump>`). The importer only ever writes to
the target shop, so no other shop is affected.

## Tunables
- `WAREHOUSE_IMPORT_RATE_PER_MIN` — keep ≤ the server limit (default 600/min). `540` is safe.
- `WAREHOUSE_IMPORT_DEFAULT_UNIT` — defaults to `dona`.
- `WAREHOUSE_IMPORT_LIMIT` — import only the first N valid rows (testing only).

## Guards (enforced by the importer + backend)
- Production API + name-only target → **refused**.
- `shop_id` mode → requires `WAREHOUSE_IMPORT_ACCOUNT_ID`; token `accountId` must match.
- `WAREHOUSE_IMPORT_SUPERADMIN=true` → requires a `SUPER_ADMIN` token + both ids.
- Target shop must be one the authenticated account owns (else abort).
- Backend independently stamps each product's `shop_id` from `X-Shop-Id` and rejects a
  cross-account `X-Shop-Id` — a product **cannot** land in another shop/account.
- Per-shop count guard: any non-target shop change → **STOP**.
- Unit tests: `PYTHONUTF8=1 python imports/test_warehouse_import.py`.
