# Verification Checklist (run on the NEW server before cutover)

Tick every box. If any FAILS → **STOP**, keep using the OLD server, investigate.

## App & auth
- [ ] Home page loads: `https://__NEW_HOST__/` → 200
- [ ] Login works (owner + super-admin)
- [ ] Dashboard loads after login
- [ ] Warehouse page loads
- [ ] `/api/products` **without** auth returns **401**

## Data integrity (must match database/expected-counts.md)
- [ ] total products = **6618**
- [ ] shop 6 products = **6598**
- [ ] product search by **barcode** works (e.g. `17054` → 1 hit)
- [ ] product search by **name** works (e.g. `COLGATE` → multiple hits)
- [ ] POS search works
- [ ] accounts = 13, shops = 15

## Accounting
- [ ] accounting reports load (trial-balance, balance-sheet, profit-loss)
- [ ] trial balance `balanced = true`
- [ ] balance sheet `balanced = true`
- [ ] inventory value = **262,557,334.87** (shop 6)

## Security / hardening
- [ ] `/v3/api-docs` does **NOT** return OpenAPI JSON (SPA HTML is OK; check content-type/body)
- [ ] security headers present: `Strict-Transport-Security`, `Content-Security-Policy`, `Referrer-Policy`, `Permissions-Policy`, `X-Frame-Options`, `X-Content-Type-Options`
- [ ] evil-origin CORS rejected; approved origin echoed
- [ ] backend health = 200 (`/actuator/health`)
- [ ] license health = 200
- [ ] backend listens on **127.0.0.1:8086** only (`ss -tlnp | grep 8086`)
- [ ] license listens on **127.0.0.1:9090** only
- [ ] public ports = 22, 80, 443 only (`ufw status`); 8086/9090 NOT reachable externally

## Backups / rollback readiness
- [ ] PostgreSQL restored; counts verified
- [ ] license H2 file copied to `/opt/savdopro/license-data.mv.db`
- [ ] a fresh dump of the NEW DB taken before go-live
- [ ] **OLD server still online and untouched** (rollback fallback)

See `post-deploy-smoke-test.md` for the exact commands.
