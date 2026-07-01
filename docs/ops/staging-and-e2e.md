# Staging stack + Playwright E2E

## Nima uchun

Unit-testlar SPA bundle buzilishini, license↔backend JWT nomuvofiqligini,
o'lik route/chunklarni ushlamaydi. Buning uchun: **haqiqiy imagelardan
yig'ilgan staging stack** + **Playwright smoke** (landing → login →
dashboard → POS). CI'da har push/PR'da `e2e` jobi shuni bajaradi;
deploy.yml endi test-suitlar o'tmaguncha jar'ni serverga chiqarmaydi.

## Staging stackni lokal ko'tarish

```bash
docker compose -f docker-compose.staging.yml up -d --build
# UI:    http://localhost:28086/login?licenseUrl=http://localhost:19090
# Login: demo_owner / DemoStaging2026   (DEMO_SEED_PASSWORD bilan almashtiriladi)
# Reset: docker compose -f docker-compose.staging.yml down -v
```

Xususiyatlari:
- H2 fayllar konteyner ichida `/tmp` da (runtime imagelar non-root, `/app`
  yozib bo'lmaydi) — `down -v && up` = toza, qayta seed qilingan muhit.
- `ALLOW_DEMO_SEED=true`: backend 90001/90002 demo tenantlarni (mahsulot,
  savdo tarixi, qarz), license esa `demo_owner`/`demo_kassir`/`demo_owner_b`
  loginlarini yaratadi. Prod profil ostida seedlar QATTIQ o'chirilgan.
- SPA `VITE_TARGET=web` bilan quriladi (landing + register), lekin
  `VITE_LICENSE_URL` ataylab bo'sh — license manzili `?licenseUrl=` query
  parametri orqali beriladi (licenseClient.js localStorage'ga saqlaydi).
- Postgres YO'Q — DB-engine pariteti prod compose (docker-compose.yml)
  zimmasida; staging maqsadi UI/auth/flow smoke.

## Playwright'ni ishga tushirish

```bash
cd frontend
npm ci
npx playwright install chromium   # bir marta
npm run e2e                       # docker staging 28086/19090 da turgan bo'lsin
npm run e2e:headed                # brauzerni ko'rib debug qilish
```

Env sozlamalari (defaultlari staging compose bilan mos):
`E2E_BASE_URL`, `E2E_LICENSE_URL`, `DEMO_SEED_PASSWORD`, `E2E_DEMO_USER`.

### Docker'siz (tez lokal aylanish)

Vite dev server `/api` ni 8086 ga proxy qiladi, shuning uchun jarayon:
backend + license'ni demo-seed bilan lokal ishga tushiring, `npm run dev`,
so'ng `E2E_BASE_URL=http://localhost:3000 E2E_LICENSE_URL=http://localhost:9090 npm run e2e`.
(Landing testi bunda o'tadi, chunki dev build IS_WEB emas — `--grep -v landing`
bilan o'tkazib yuboring yoki VITE_TARGET=web bilan dev server oching.)

## CI'dagi joyi

- `.github/workflows/ci.yml` → `e2e` jobi: staging compose build+up, health
  kutish, Playwright chromium, xatoda compose loglari + playwright-report
  artefakti.
- `.github/workflows/deploy.yml` → `test-backend` + `test-license` jobs
  deploy'dan OLDIN o'tishi shart (`needs:`). Endi qizil suite bilan v* tag
  bosilsa ham prod'ga hech narsa chiqmaydi.

## Selektor siyosati

Smoke spec hozircha barqaror atributlarga tayanadi (`autocomplete`,
`placeholder`, brand matni). Chuqurroq flow testlari (checkout, qaytarish,
qarz) uchun komponentlarga `data-testid` qo'shib boring — matn/tarjima
o'zgarishi testni sindirmasin.
