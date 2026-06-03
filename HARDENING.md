# SavdoPRO — Mustahkamlik va ishonchlilik (12 punkt)

Holat: ✅ bajarildi · 🟡 men tayyorladim, sizning amalingiz kerak · 📋 tayyor-kod (qo'llashga tayyor)

Server: DigitalOcean `167.172.164.214` · SSH: `ssh -i ~/.ssh/savdopro_vps root@167.172.164.214`

---

## 1. ✅ Build ishonchliligi + CI deploy
**Muammo (2026-06-04 outage):** `vite-plugin-javascript-obfuscator` Vite code-splitting'ni buzardi — lazy chunklarni tashlab/birlashtirib yuborardi (Warehouse 963KB ga shishar, Dashboard chunk yo'qolardi), natijada sahifalar 404 berib ishlamasdi. **Intermittent** — goh ishlar, goh yo'q.

**Bajarildi:**
- `vite.config.js`: obfuscatsiya **opt-in** (`VITE_OBFUSCATE=1`), default OFF → **har bir build to'liq va ishonchli**. Himoya saqlandi: esbuild name-mangling + console drop, source-map yo'q, private repo.
- `.github/workflows/deploy.yml`: frontend build'dan keyin **chunk-to'liqligi tekshiruvi** (Dashboard/ProductEditor/Payments/... + ≥25 chunk) — to'liqsiz build **deploy qilinmaydi**.

**Sizning amalingiz (🟡):** Endi qo'lda emas, **CI orqali deploy qiling:** GitHub → Actions → **Deploy** → Run workflow → branch `feat/web-merchant-portal`. Toza serverда build (ishonchli) + SHA + health-gate.

---

## 2. ✅ Backup — Postgres + off-site + rotatsiya
**Tekshirildi:** `/opt/barakat/backup.sh` har kuni 03:30 da **jonli Postgres**ni dump qiladi (gzip, ~11KB), 14 tagacha saqlaydi. Ishlaydi (qo'lda sinadim).
**Bajarildi:** scriptga **off-site** qo'shdim — dump **Telegram**ga (`@aitrading_full_bot`) hujjat sifatida yuboriladi (droplet o'lsa ham backup tirik qoladi).
**Sizning amalingiz (🟡):** Telegram'da **`@aitrading_full_bot`ga `/start`** yuboring → keyin chat-id ni o'rnatamiz → off-site va alertlar ishlaydi (4.2-bandga qarang).
**Tavsiya:** oyiga bir marta **restore'ni sinab ko'ring**: `gunzip -c /opt/barakat/backups/pg-barakat-*.sql.gz | psql -d test_restore`.

---

## 3. 🟡 Droplet'ni kattalashtirish (1GB → 2-4GB) — ENG MUHIM
1GB'da 2 Spring app + Postgres juda tor (hozir ~128MB bo'sh). **#1 reliability xavfi.**
**Qadamlar:** DigitalOcean → Droplets → `ubuntu-...` → **Resize** → 2GB yoki 4GB (CPU-Optimized shart emas, Basic) → Resize. ~5 daqiqa downtime. Oyiga ~$12-24.
Keyin (server'da): `systemctl edit savdopro-backend` → `MemoryMax`ni oshiring (masalan 900M), `-Xmx`ni `application` argida 700m ga.

## 4. 🟡 Staging muhit (prod'da test qilmaslik)
Hozir o'zgarishlar to'g'ridan prod'ga. **Variant A (arzon):** 2-chi kichik droplet (staging.savdopro) → CI'da staging branch. **Variant B (bepul):** lokalda `docker-compose` (Postgres + backend + license) → avval u yerda sinash. Xohlasangiz docker-compose faylini yozib beraman.

## 5. ✅ Login brute-force himoyasi — ALLAQACHON BOR
`license-server/.../auth/LoginRateLimiter.java`: IP'ga **10 urinish / 5 daqiqa → 15 daqiqa bloklash**. AuthController'ga ulangan, testi bor (`LoginRateLimiterTest`), + `SuspiciousLoginAlerter`. **Qo'shimcha ish kerak emas.**

## 6. 📋 Sentry (xato monitoring) — qo'llashga tayyor
Bepul Sentry loyihasi oching (sentry.io) → DSN oling. Keyin:
- **Backend** `pom.xml`: `<dependency><groupId>io.sentry</groupId><artifactId>sentry-spring-boot-starter-jakarta</artifactId><version>7.14.0</version></dependency>` + `application.properties`: `sentry.dsn=${SENTRY_DSN:}` `sentry.traces-sample-rate=0.1` (DSN bo'sh bo'lsa — no-op).
- **Frontend** `npm i @sentry/react` + `main.jsx`da: `if(import.meta.env.VITE_SENTRY_DSN) Sentry.init({dsn:..., tracesSampleRate:0.1})`.
DSN bersangiz — men 10 daqiqada ulab beraman. *(1GB droplet'da avval #3 ni qiling — Sentry biroz yuk qo'shadi.)*

## 7. ✅ Uptime monitoring — BOR (chat sozlash kerak)
`/opt/barakat/watch.sh` har 3 daqiqa backend/license/postgres/https tekshiradi, **holat o'zgarganда** Telegram alert yuboradi (🔴 ishlamayapti / 🟢 tiklandi), spam yo'q.
**Muammo:** alert chat `5035317446` — **"chat not found"** (bot bilan yozishilmagan).
**4.2 — Sizning amalingiz (🟡):** `@aitrading_full_bot`ga `/start` yuboring. Keyin men shu buyruq bilan chat-id ni o'rnataman:
```
ssh ... 'TOKEN=$(grep "^telegram.bot-token=" /opt/barakat/application-local.properties|cut -d= -f2-); curl -s "https://api.telegram.org/bot$TOKEN/getUpdates"'
```
→ chiqgan `chat.id` ni `application-local.properties` dagi `telegram.chat-ids=` ga yozib, backend restart. Shunda **alertlar + off-site backup + egasi-signallari** (kamomad, zararga-sotuv, kunlik hisobot) — hammasi ishlaydi.

## 8. 🟡 Dependency audit — CVE'lar topildi
`npm audit`: **5 ta zaiflik** (1 critical, 1 high) — hammasi `exceljs` → `uuid` (eskirgan) sababli. exceljs Excel-eksport uchun ishlatiladi.
**Tavsiya:** `exceljs`ni so'nggi versiyaga yangilash (`npm i exceljs@latest`) → eksportni sinab ko'rish. Buzilmasa — CVE'lar yopiladi. Backend: `mvn versions:display-dependency-updates` bilan eski kutubxonalarni ko'ring.
Xohlasangiz — men exceljs'ni yangilab, eksportni sinab beraman.

## 9. ✅/🟡 CI testlar
**37 ta test fayl bor** (backend + license). Hozir `deploy.yml` `-DskipTests` bilan deploy qiladi.
**Bajarildi:** CI'ga build-to'liqligi tekshiruvi qo'shildi (1-band).
**Tavsiya (🟡):** `.github/workflows/ci.yml` (push'da) testlarni ishga tushirsin; deploy esa test o'tgandan keyingina. Hozir testlar deploy'ni bloklamaydi — ci.yml'ni tekshirib, kerak bo'lsa test-gate qo'shaman.

## 10. 📋 Zero-downtime deploy
Hozir har deployда ~60s uzilish (kassir o'rtada qoladi). **Yechim:** ikkinchi backend instance (port 8087) + nginx upstream → yangi jar'ni 8087 da ko'tarib, health 200 bo'lgach nginx'ni unga o'tkazish (eski'ni o'chirish). #3 (kattaroq droplet) shart, chunki 2 instance ko'proq RAM oladi.

## 11. 🟡 Managed Postgres (DigitalOcean)
Hozir Postgres app bilan bir droplet'da (resurs raqobati + manual backup). **Managed DB:** avto-backup (PITR), failover, alohida resurs. ~$15/oy. Migratsiya: managed DB yaratish → `pg_dump | psql` → `application-local.properties` datasource URL'ni yangilash. Xohlasangiz migratsiya skriptini yozib beraman.

## 12. 🟡 Offline rejim (kassir uchun)
**Bor:** `client_ref` (idempotency key) — offline checkout replay (V27). Internet qaytganda dublikatsiz qayta yuboriladi.
**Kuchaytirish:** to'liq offline-first (Service Worker + IndexedDB cart cache) — server o'chsa ham kassir sotuvni davom ettira olsin, qaytganda sinxron. Bu katta ish; alohida reja sifatida.

---

## Yakuniy ustuvorlik (tavsiya)
1. **CI orqali deploy** (1-band) — qo'lda deploy'ni butunlay to'xtating.
2. **`/start` botga** (7-band) — alertlar + off-site backup + egasi signallarini yoqadi.
3. **Droplet 2-4GB** (3-band) — eng katta reliability xavfini yopadi.
4. exceljs CVE (8) + Sentry (6) + restore-test (2) — keyingi hafta.
5. Zero-downtime (10) + managed DB (11) + offline (12) — masshtab oshganда.
