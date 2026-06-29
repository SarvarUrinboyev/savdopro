"""
SavdoPRO — safe Warehouse Excel importer (upsert by code, never deletes).

SAFETY
------
- Refuses to run unless  ALLOW_WAREHOUSE_IMPORT=true.
- Targets exactly one shop, resolved by name (WAREHOUSE_IMPORT_SHOP_NAME);
  aborts if that shop is not found. Never touches other shops.
- Upserts by product code (Excel "КОД" -> Product.barcode): existing code in the
  shop is UPDATED in place; unknown code is CREATED. Never duplicates.
- Never deletes products that are missing from the Excel.
- Talks only to the API/license URLs you give it (default = localhost dev),
  so it can run against local/staging without ever reaching production.

REQUIRED ENV
------------
  ALLOW_WAREHOUSE_IMPORT=true
  WAREHOUSE_IMPORT_FILE=imports/Товары.xlsx
  WAREHOUSE_IMPORT_SHOP_NAME=Asosiy do'kon
  WAREHOUSE_IMPORT_USER=<owner username>
  WAREHOUSE_IMPORT_PASSWORD=<owner password>
OPTIONAL ENV
  WAREHOUSE_IMPORT_API_URL=http://127.0.0.1:8086
  WAREHOUSE_IMPORT_LICENSE_URL=http://127.0.0.1:19090
  WAREHOUSE_IMPORT_DEFAULT_UNIT=dona
  WAREHOUSE_IMPORT_LIMIT=<N>           # import only first N valid rows (testing)
  WAREHOUSE_IMPORT_PREVIEW=true        # do everything except writes (dry preview)

Mapping decisions (source has no clean field for these — flagged in the report):
- "Ед.из" is a numeric MXIK package code, not a human unit  -> unit defaults to "dona".
- Fractional stock is rounded to the nearest integer (Product.quantity is int).
- Category "Корневая группа" (root group) -> no category (uncategorized).
"""
import json
import os
import sys
import time
import warnings

warnings.filterwarnings("ignore")
import openpyxl
import requests

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "_import")
os.makedirs(OUT, exist_ok=True)

CFG = {
    "file": os.environ.get("WAREHOUSE_IMPORT_FILE", os.path.join(HERE, "Товары.xlsx")),
    "shop": os.environ.get("WAREHOUSE_IMPORT_SHOP_NAME", "Asosiy do'kon"),
    "user": os.environ.get("WAREHOUSE_IMPORT_USER", ""),
    "pw": os.environ.get("WAREHOUSE_IMPORT_PASSWORD", ""),
    "api": os.environ.get("WAREHOUSE_IMPORT_API_URL", "http://127.0.0.1:8086").rstrip("/"),
    "lic": os.environ.get("WAREHOUSE_IMPORT_LICENSE_URL", "http://127.0.0.1:19090").rstrip("/"),
    "unit": os.environ.get("WAREHOUSE_IMPORT_DEFAULT_UNIT", "dona"),
    "limit": int(os.environ.get("WAREHOUSE_IMPORT_LIMIT", "0") or "0"),
    "preview": os.environ.get("WAREHOUSE_IMPORT_PREVIEW", "false").lower() == "true",
    # Stay under the backend's per-caller rate limit (default 600/min). 0 = no
    # throttle (use only when the server limit is raised/disabled for staging).
    "rate_per_min": int(os.environ.get("WAREHOUSE_IMPORT_RATE_PER_MIN", "540") or "0"),
    "retries": int(os.environ.get("WAREHOUSE_IMPORT_RETRIES", "6")),
}
ROOT_CATEGORY = "Корневая группа"  # treated as "no category"

HEADER_ALIASES = {
    "code": ["код", "kod", "code"], "name": ["товар", "наименование", "name"],
    "category": ["категория", "kategoriya"], "qty": ["остаток", "qoldiq", "stock"],
    "cost": ["себестоимость", "tannarx", "cost"], "sell": ["цена", "narx", "sell", "price"],
    "mxik": ["икпу", "ikpu", "mxik"], "unit": ["ед.из", "ед.изм", "unit"],
    "minstock": ["мин.остаток", "min", "minimal"],
}


def norm(s):
    return ("" if s is None else str(s)).strip().lower()


def to_num(v):
    if v is None:
        return None
    if isinstance(v, (int, float)):
        return float(v)
    s = str(v).strip().replace(" ", "").replace(" ", "").replace(",", ".")
    try:
        return float(s) if s else None
    except ValueError:
        return None


def parse_excel(path):
    wb = openpyxl.load_workbook(path, data_only=True)
    ws = wb[wb.sheetnames[0]]
    rows = list(ws.iter_rows(values_only=True))
    flat = {a: f for f, al in HEADER_ALIASES.items() for a in al}
    hidx, colmap, hits = 0, {}, -1
    for i, row in enumerate(rows[:25]):
        cm = {}
        for j, c in enumerate(row):
            k = norm(c)
            if k in flat and flat[k] not in cm:
                cm[flat[k]] = j
        if len(cm) > hits:
            hits, hidx, colmap = len(cm), i, cm
    valid, invalid, frac, negative_corrected = [], [], 0, []

    def cell(row, f):
        j = colmap.get(f)
        return row[j] if j is not None and j < len(row) else None

    for n, row in enumerate(rows[hidx + 1:]):
        excel_row = hidx + 1 + n + 1
        code = "" if cell(row, "code") is None else str(cell(row, "code")).strip()
        name = "" if cell(row, "name") is None else str(cell(row, "name")).strip()
        if code == "" and name == "":
            continue
        qty, cost, sell = to_num(cell(row, "qty")), to_num(cell(row, "cost")), to_num(cell(row, "sell"))
        minst = to_num(cell(row, "minstock"))
        mxik = cell(row, "mxik"); mxik = "" if mxik is None else str(mxik).strip()
        cat = cell(row, "category"); cat = "" if cat is None else str(cat).strip()
        problems = []
        if code == "": problems.append("code missing")
        if name == "": problems.append("name missing")
        if qty is None: problems.append("qty not numeric")
        if cost is None: problems.append("cost not numeric")
        elif cost < 0: problems.append("cost negative")
        if sell is None: problems.append("sell not numeric")
        elif sell < 0: problems.append("sell negative")
        rec = {"excel_row": excel_row, "code": code, "name": name, "category": cat,
               "qty": qty, "qty_original": qty, "cost": cost, "sell": sell,
               "mxik": mxik, "minstock": minst, "negative_corrected": False,
               "problems": problems}
        if problems:
            invalid.append(rec)
            continue
        # Negative stock is a data artefact (oversold rows). Correct it to 0 and
        # REPORT it (never silently hidden) — the row is still imported.
        if qty < 0:
            rec["negative_corrected"] = True
            rec["qty"] = 0.0
            negative_corrected.append(rec)
        if rec["qty"] != round(rec["qty"]):
            frac += 1
        valid.append(rec)
    wb.close()
    return valid, invalid, frac, hidx + 1, colmap, negative_corrected


class Api:
    def __init__(self, cfg):
        self.cfg = cfg
        self.s = requests.Session()
        self.token = None
        self.shop_id = None
        self._interval = 60.0 / cfg["rate_per_min"] if cfg["rate_per_min"] > 0 else 0.0
        self._last = 0.0

    def _throttle(self):
        if self._interval:
            dt = time.time() - self._last
            if dt < self._interval:
                time.sleep(self._interval - dt)
            self._last = time.time()

    def _send(self, method, url, **kw):
        """Request with client-side throttle + 429 retry/backoff."""
        kw.setdefault("timeout", 30)
        resp = None
        for attempt in range(self.cfg["retries"]):
            self._throttle()
            resp = self.s.request(method, url, **kw)
            if resp.status_code == 429:
                time.sleep(min(30.0, 2.0 ** attempt + 0.5))
                continue
            return resp
        return resp

    def login(self):
        r = self.s.post(f"{self.cfg['lic']}/api/auth/login",
                        json={"username": self.cfg["user"], "password": self.cfg["pw"]},
                        timeout=15)
        r.raise_for_status()
        self.token = r.json()["token"]
        self.s.headers.update({"Authorization": f"Bearer {self.token}"})

    def _h(self):
        return {"X-Shop-Id": str(self.shop_id)} if self.shop_id else {}

    def resolve_shop(self, name):
        r = self.s.get(f"{self.cfg['api']}/api/shops", timeout=15)
        r.raise_for_status()
        shops = r.json()
        match = [s for s in shops if str(s.get("name", "")).strip() == name.strip()]
        if not match:
            avail = [s.get("name") for s in shops]
            raise SystemExit(f"ABORT: target shop {name!r} not found. Available: {avail}")
        if len(match) > 1:
            raise SystemExit(f"ABORT: {len(match)} shops named {name!r} — ambiguous.")
        self.shop_id = match[0]["id"]
        return match[0]

    def existing_products(self):
        r = self.s.get(f"{self.cfg['api']}/api/products", headers=self._h(), timeout=60)
        r.raise_for_status()
        out = {}
        for p in r.json():
            bc = (p.get("barcode") or "").strip()
            if bc:
                out[bc] = p
        return out

    def create(self, payload):
        r = self._send("POST", f"{self.cfg['api']}/api/products", headers=self._h(), json=payload)
        r.raise_for_status()
        return r.json()

    def update(self, pid, payload):
        r = self._send("PUT", f"{self.cfg['api']}/api/products/{pid}", headers=self._h(), json=payload)
        r.raise_for_status()
        return r.json()

    def stocktake(self, counts):
        r = self._send("POST", f"{self.cfg['api']}/api/products/stocktake", headers=self._h(),
                       json={"counts": counts}, timeout=120)
        r.raise_for_status()
        return r.json()

    def categories(self):
        r = self.s.get(f"{self.cfg['api']}/api/categories", headers=self._h(), timeout=30)
        r.raise_for_status()
        return r.json()

    def create_category(self, name):
        r = self._send("POST", f"{self.cfg['api']}/api/categories", headers=self._h(),
                       json={"name": name})
        r.raise_for_status()
        return r.json()

    def resolve_categories(self, names):
        """Map each category name -> id, creating any that don't exist yet."""
        existing = {c["name"].strip().lower(): c["id"] for c in self.categories()}
        out = {}
        for name in names:
            key = name.strip().lower()
            if key in existing:
                out[name] = existing[key]
            else:
                cid = self.create_category(name)["id"]
                existing[key] = cid
                out[name] = cid
        return out


def payload_for(rec, cfg, cat_map=None):
    cat = rec["category"]
    is_real = cat not in ("", ROOT_CATEGORY)
    # Send an explicit categoryId so BOTH create and update set it (the backend's
    # categoryName resolve-or-create only fires on create, so an idempotent
    # re-import would otherwise null the category on update).
    category_id = (cat_map or {}).get(cat) if is_real else None
    return {
        "name": rec["name"],
        "barcode": rec["code"],
        "purchasePrice": round(rec["cost"], 2),
        "salePrice": round(rec["sell"], 2),
        "quantity": int(round(rec["qty"])),
        "categoryId": category_id,
        "categoryName": cat if (is_real and category_id is None) else None,
        "lowStockThreshold": int(round(rec["minstock"])) if rec["minstock"] is not None else 0,
        "mxikCode": rec["mxik"] or None,
        "unit": cfg["unit"],
        "requiresImei": False,
    }


def main():
    if os.environ.get("ALLOW_WAREHOUSE_IMPORT", "").lower() != "true":
        raise SystemExit("REFUSING: set ALLOW_WAREHOUSE_IMPORT=true to run the importer.")
    if not CFG["user"] or not CFG["pw"]:
        raise SystemExit("REFUSING: WAREHOUSE_IMPORT_USER / WAREHOUSE_IMPORT_PASSWORD required.")

    print(f"File   : {CFG['file']}")
    print(f"Shop   : {CFG['shop']!r}")
    print(f"API    : {CFG['api']}   License: {CFG['lic']}")
    print(f"Preview: {CFG['preview']}   Limit: {CFG['limit'] or 'all'}")
    valid, invalid, frac, header_row, colmap, negative_corrected = parse_excel(CFG["file"])
    print(f"Parsed : valid={len(valid)} invalid={len(invalid)} "
          f"negative_stock_corrected_to_zero={len(negative_corrected)} "
          f"fractional_qty_rounded={frac} header_row={header_row}")
    if CFG["limit"]:
        valid = valid[:CFG["limit"]]

    api = Api(CFG)
    api.login()
    shop = api.resolve_shop(CFG["shop"])
    print(f"Shop OK: id={shop['id']} name={shop.get('name')!r} main={shop.get('main')}")
    existing = api.existing_products()
    print(f"Existing products in shop: {len(existing)}")

    # Pre-resolve real categories to ids (create missing) so create AND update
    # both set categoryId. "Корневая группа" (root) stays uncategorised.
    cat_names = sorted({r["category"] for r in valid
                        if r["category"] not in ("", ROOT_CATEGORY)})
    # Preview is strictly read-only: don't create categories on a dry-run.
    cat_map = ({} if CFG["preview"]
               else api.resolve_categories(cat_names) if cat_names else {})
    print(f"Categories resolved/created: {len(cat_map)} -> {list(cat_map.keys())}")

    created = updated = skipped = 0
    errors = []
    qty_fixes = []
    for i, rec in enumerate(valid):
        payload = payload_for(rec, CFG, cat_map)
        try:
            if rec["code"] in existing:
                pid = existing[rec["code"]]["id"]
                if not CFG["preview"]:
                    api.update(pid, payload)
                    cur = int(existing[rec["code"]].get("quantity") or 0)
                    if cur != payload["quantity"]:
                        qty_fixes.append({"productId": pid, "actual": payload["quantity"]})
                updated += 1
            else:
                if not CFG["preview"]:
                    api.create(payload)
                created += 1
        except requests.HTTPError as e:
            body = e.response.text[:200] if e.response is not None else str(e)
            errors.append({"code": rec["code"], "name": rec["name"], "error": body})
            skipped += 1
        if (i + 1) % 500 == 0:
            print(f"  ...{i + 1}/{len(valid)}  created={created} updated={updated} errors={len(errors)}")

    # Correct stock on updated products whose qty drifted (re-import path).
    if qty_fixes and not CFG["preview"]:
        for k in range(0, len(qty_fixes), 500):
            api.stocktake(qty_fixes[k:k + 500])

    result = {
        "shop": {"id": shop["id"], "name": shop.get("name"), "main": shop.get("main")},
        "valid_rows": len(valid), "invalid_rows": len(invalid),
        "created": created, "updated": updated, "skipped_errors": skipped,
        "stock_corrections": len(qty_fixes), "fractional_qty_rounded": frac,
        "negative_stock_corrected_to_zero": len(negative_corrected),
        "negative_stock_rows": [
            {"excel_row": r["excel_row"], "code": r["code"], "name": r["name"],
             "original_qty": r["qty_original"], "imported_qty": 0}
            for r in negative_corrected
        ],
        "invalid_row_details": [
            {"excel_row": r["excel_row"], "code": r["code"], "name": r["name"],
             "problems": r["problems"]} for r in invalid
        ],
        "preview": CFG["preview"], "errors": errors[:50],
    }
    with open(os.path.join(OUT, "result.json"), "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print("-" * 60)
    _verbose = {"errors", "negative_stock_rows", "invalid_row_details"}
    print(json.dumps({k: v for k, v in result.items() if k not in _verbose},
                     ensure_ascii=False, indent=2))
    if errors:
        print(f"ERRORS ({len(errors)}), first 5:")
        for e in errors[:5]:
            print("   ", e)
    print(f"\nResult written to {os.path.join(OUT, 'result.json')}")


if __name__ == "__main__":
    main()
