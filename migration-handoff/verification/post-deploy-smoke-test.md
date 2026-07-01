# Post-Deploy Smoke Test (copy-paste commands)

Set your host first:
```bash
HOST=https://__NEW_HOST__
```

## 1. Health, home, unauthorized, OpenAPI
```bash
curl -s -o /dev/null -w 'home=%{http_code}\n'   $HOST/
curl -s $HOST/actuator/health                                   # {"status":"UP"}
curl -s -o /dev/null -w 'products_noauth=%{http_code}\n' $HOST/api/products   # want 401
# api-docs must be SPA HTML, NOT OpenAPI JSON:
curl -s -D- $HOST/v3/api-docs | grep -i '^content-type'         # want text/html
curl -s $HOST/v3/api-docs | grep -q '"openapi"' && echo 'FAIL: OpenAPI exposed' || echo 'OK: no OpenAPI JSON'
```

## 2. Security headers + CORS
```bash
curl -s -D- -o /dev/null $HOST/ | grep -iE \
 'strict-transport-security|content-security-policy|referrer-policy|permissions-policy|x-frame-options|x-content-type-options'
# CORS: evil rejected / approved echoed
curl -s -D- -o /dev/null -H "Origin: https://evil.example" $HOST/api/health | grep -i access-control-allow-origin && echo FAIL || echo 'OK evil rejected'
curl -s -D- -o /dev/null -H "Origin: $HOST" $HOST/api/health | grep -i access-control-allow-origin
```

## 3. Ports bound to loopback only (on the server)
```bash
ss -tlnp | grep -E ':8086|:9090'    # want 127.0.0.1:8086 and 127.0.0.1:9090
ufw status                          # want 22, 80, 443 only
```

## 4. Data + accounting (via API, logged in as the owner; or via psql)
```bash
# psql (fastest, authoritative):
sudo -u postgres psql -d barakat -tAc "SELECT count(*) FROM products;"                 # 6618
sudo -u postgres psql -d barakat -tAc "SELECT count(*) FROM products WHERE shop_id=6;" # 6598
sudo -u postgres psql -d barakat -tAc "SELECT count(*) FROM gl_journal_entry WHERE shop_id=6;" # 2780
```
UI: log in, open Warehouse (≈6598 products), search a barcode + a name, open the
POS search, open Accounting → Trial balance / Balance sheet (both must say balanced).

## 5. Service logs clean
```bash
journalctl -u savdopro-license -n 30 --no-pager | grep -i 'Started\|ERROR'
journalctl -u savdopro-backend -n 40 --no-pager | grep -i 'profile is active\|Started\|ERROR'  # profile = prod
```
