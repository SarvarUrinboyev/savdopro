# SavdoPRO Roadmap

Quyidagi reja v1.10.0 release'idan keyingi qadamlarni belgilaydi. Har bir bosqich alohida release sifatida chiqariladi.

## Phase 4.1 — F: Hisobotlar va eksport (v1.11.0, ~5 kun)

- PDF generator (iText yoki OpenPDF) — server tomonda render
- Sotuv hisoboti PDF (kunlik / haftalik / oylik)
- Inventarizatsiya PDF
- Mijoz ledger PDF
- XLSX eksport (Apache POI) — hozir CSV bor, XLSX chiroyli formatlash bilan
- Profit/Loss chart drill-down (oy → hafta → kun)
- Top-N tahlil: top mijozlar, top tovarlar, sekin sotilayotgan
- Auto-email hisoboti (haftalik egasiga, optional)

## Phase 4.2 — B: Avtomatlashtirilgan ogohlantirishlar (v1.12.0, ~6 kun)

- Low-stock alert: tovar mavjud miqdori threshold dan kam bo'lsa → egasiga Telegram
- Auto-reorder takliflari: sotuv tezligiga qarab "bu hafta sotib oling" tavsiyasi
- Smena yakunida avto-hisobot: kunlik PDF + Telegram'ga jo'natish (4.1 ga bog'liq)
- Obuna tugashiga 7 kun qolganda License Server SMS yoki email
- Failed login attempts > 5 → super-admin'ga ogohlantirish

## Phase 4.3 — E: Hardware integratsiyalar (v1.13.0, ~5 kun)

- ESC/POS thermal printer driver (Xprinter, Star, Epson)
- Cash drawer trigger: chek chop etilganda avto-ochish
- USB barcode scanner auto-focus: skaner = klaviatura emulyatsiyasi, fokus boshqaruvi
- Elektron tarozi integratsiyasi (CAS, Mettler) — sabzavot/go'sht uchun

## Phase 4.4 — C: Loyalty + Discount + 2-ekran (v1.14.0, ~8-10 kun)

- Skidka engine: foiz / aniq summa / "X olsang Y bepul"
- Mijoz loyalty card: har xaridda ball, ball orqali chegirma
- Wholesale narx darajalari: mijoz toifasiga qarab maxsus narx
- 2-ekran kiosk display: skanerlanayotgan tovar + yig'indi katta ekranda
- Multi-currency snapshot: USD/UZS sotuv paytidagi rate bilan saqlash

## Phase 4.5 — G: Auth & xavfsizlik (v1.15.0, ~10 kun)

- SMS code login: telefon + SMS code (parol o'rniga)
- 2FA super-admin uchun: TOTP (Google Authenticator)
- Telegram OAuth: mijoz Telegram bilan kirsin
- Per-user granular permissions (ko'rish / o'zgartirish per-bo'lim)
- BCrypt cost 10 → 12
- Min password 4 → 8 + complexity rules
- Audit log CSV eksport

## Phase 4.6 — H: SaaS biznes (v2.0.0, ~12 kun)

- Subscription auto-renewal: Click / Payme integratsiyasi
- White-label per-account: logo, ranglar, brand nomi mijozning
- Tenant data eksport (GDPR-style): mijoz "menga barcha datamni bering"
- Audit log CSV / PDF eksport super-admin uchun
- Multi-language admin panel (uz/ru/en)

## Phase 4.7 — A: Soliq + Didox (v2.1.0, ~10-12 kun)

> Buxgalter konsultatsiyasidan keyin boshlanadi. API hujjatlari va sandbox kerak.

- Didox API integratsiyasi: faktura yuborish, EHF qabul qilish
- Soliq onlayn-kassa (Y-tag) — per-shop opt-in, fiscalization
- IKPU avtomatik qidirish: mahsulot yaratganda tax.uz dan
- QQS hisob-kitobi: per-product VAT, hisobotda alohida

## Phase 5 — D: SavdoPRO Mobile (alohida loyiha)

> Alohida repo. React Native (yoki Flutter). iOS + Android.

- iOS / Android: savdo monitoring, dashboard, qoldiqlar
- Telefon barcode skaner: inventarizatsiya, qabul qilishda
- Owner uchun real-time push: kunlik tushum, kritik alerts
- App Store ($99/yil) + Play Store ($25 bir martalik) hisoblari
