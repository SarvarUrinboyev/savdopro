# Message to the deploying engineer

---

**EN**

Hey — I need you to deploy **SavdoPRO** (a Spring Boot POS/warehouse SaaS,
release **v2.3.4**) to a fresh server. Everything you need is in the
`migration-handoff/` folder; start with `README_DEPLOY.md`.

**What it is:** two Java 21 services — `savdopro-backend` (port 8086, PostgreSQL,
serves the bundled React app) and `savdopro-license` (port 9090, auth/billing,
H2 file). Both bind to **localhost**; **nginx** is the only public ingress (HTTPS).

**What I'll send you (separately, securely — not in the folder):**
- GitHub repo access (or the two prebuilt jars)
- the DB dump `barakat_migration_20260701_1714.dump`
- the license file `license-data_migration_20260701_1714.mv.db`
- the public host/IP to use, and the real secret values to put in the env files
  (I'll send those over a secure channel — they are NOT in the package)

**Server:** Ubuntu 24.04, Java 21, PostgreSQL 16, nginx, ufw. RAM ≥ 2 GB (4 GB
better), disk ≥ 40 GB. Open **only** ports 22/80/443; keep 8086/9090 localhost-only.

**Deploy:** do a **manual jar deploy** first (fastest — `github-actions/deploy-options.md`),
wire GitHub Actions later. Restore the DB, drop in the H2 file, fill the two
`/etc/savdopro/*.env` files (chmod 600), install the systemd units + nginx, get a
cert with certbot, start **license then backend**.

**Verify** against `verification/verification-checklist.md` — especially: total
products = **6618**, shop 6 = **6598**, accounting trial/balance sheet balanced,
`/api/products` unauth = 401, no OpenAPI JSON, security headers present, ports
localhost-only.

**Do NOT:** touch or stop the OLD server, change DNS, run the Excel import again,
run the ledger backfill again, or commit any secret. The old server stays live
until the new one passes every check — that's our rollback.

---

**UZ**

Salom — **SavdoPRO** (v2.3.4, Spring Boot POS/ombor tizimi) ni yangi serverga
o'rnatib ber. Hammasi `migration-handoff/` papkasida, `README_DEPLOY.md` dan boshla.

Ikki xizmat: **backend** (8086, PostgreSQL `barakat`) va **license** (9090, H2
fayl). Ikkalasi ham **localhost**da, tashqariga faqat **nginx** (HTTPS) chiqaradi.

**Men senga alohida (xavfsiz) yuboraman:** GitHub ruxsati yoki 2 ta tayyor jar,
DB dump, license `.mv.db` fayli, public host/IP va env fayllar uchun maxfiy
qiymatlar (maxfiy qiymatlar papkada YO'Q).

**Server:** Ubuntu 24.04, Java 21, PostgreSQL 16, nginx, ufw. RAM ≥ 2 GB (4 GB
yaxshi), disk ≥ 40 GB. Faqat 22/80/443 portlar ochiq; 8086/9090 faqat localhost.

**Tekshir:** mahsulotlar jami **6618**, 6-do'kon **6598**, buxgalteriya balansi
"balanced", `/api/products` (autentifikatsiyasiz) = 401, OpenAPI JSON ko'rinmasin.

**Qilma:** eski serverga tegma / o'chirma, DNS o'zgartirma, Excel importni qayta
yurgizma, ledger backfillni qayta yurgizma, maxfiy qiymatlarni git'ga qo'yma.
Eski server yangi server to'liq testdan o'tguncha ishlab turadi.
