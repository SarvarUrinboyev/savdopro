# Barakat Market — Mobile

Mijozlar uchun **xarid va yetkazib berish** mobil ilovasi (Korzinka tipidagi B2C
ilova). Bu loyiha mavjud **Barakat SuperMarket** (egasi uchun POS/moliya tizimi)
dan alohida mahsulot — lekin bir xil brend va texnologiya stacki bilan mos.

> ⚖️ **Eslatma:** Bu ilova Korzinka yoki boshqa biror tarmoqning kodi/dizayni/logosi
> ko'chirilgan emas. Faqat oziq-ovqat yetkazib berish ilovalarida umumiy bo'lgan
> **standart funksiyalar to'plami** Barakat brendida va o'z kodimizda qayta qurilgan.

---

## Tarkibi

```
mobile/
├── app/        Flutter mijoz ilovasi (Android + iOS)
├── server/     Spring Boot mobil backend (REST API, port 8090)
├── docs/       Arxitektura va API shartnomasi
└── README.md
```

## Texnologiyalar

| Qism      | Texnologiya                          |
| --------- | ------------------------------------ |
| Mobil app | Flutter 3.x + Dart                   |
| State     | Riverpod                             |
| Network   | Dio + Retrofit-uslub klient          |
| Routing   | go_router                            |
| Backend   | Java 21 + Spring Boot 3.3            |
| DB (dev)  | H2 (fayl)                            |
| DB (prod) | PostgreSQL 16                        |
| Auth      | Telefon + SMS OTP → JWT             |

## Brend

| Element        | Qiymat                          |
| -------------- | ------------------------------- |
| Primary        | `#33608F` → `#15293F` (gradient)|
| Accent (yashil)| `#22C55E`                       |
| Til            | O'zbek (asosiy), keyin ru/en    |
| Valyuta        | UZS (so'm)                      |

## Asosiy foydalanuvchi oqimi (MVP)

1. **Splash → Onboarding** (birinchi kirishda)
2. **Telefon + OTP** orqali ro'yxatdan o'tish / kirish
3. **Bosh sahifa**: bannerlar, kategoriyalar, mashhur mahsulotlar
4. **Katalog**: kategoriya bo'yicha mahsulotlar, qidiruv
5. **Mahsulot sahifasi**: rasm, narx, ta'rif, savatga qo'shish
6. **Savatcha**: miqdorni o'zgartirish, jami summa
7. **Buyurtma rasmiylashtirish**: manzil, yetkazish vaqti, to'lov turi
8. **Buyurtmalarim**: holat kuzatuvi (qabul qilindi → yig'ilmoqda → yo'lda → yetkazildi)
9. **Profil**: manzillar, sozlamalar, chiqish

## Ishga tushirish

### Backend (port 8090) — Java 21 kerak

```bash
cd server
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Birinchi ishga tushishda H2 bazasi yaratiladi va namuna kategoriya/mahsulotlar
bilan to'ldiriladi. API: `http://localhost:8090/api`
Sog'liq tekshiruvi: `GET http://localhost:8090/api/health`

### Flutter app — Flutter SDK kerak

> ⚠️ Flutter SDK hozircha ushbu kompyuterda o'rnatilmagan. Kod yozilgan, lekin
> ishga tushirish uchun Flutter o'rnatish kerak (`docs/SETUP_FLUTTER.md` ga qarang).

```bash
cd app
flutter pub get
flutter run                   # emulyator yoki ulangan qurilmada
```

API manzilini `app/lib/core/config/app_config.dart` da sozlang
(emulyator uchun `http://10.0.2.2:8090/api`).

## Holat (progress)

- [x] Loyiha tuzilmasi va hujjatlar
- [x] Backend: domain model + auth (OTP) + katalog + savat + buyurtma
- [x] Flutter: tema, routing, API klient, asosiy ekranlar
- [ ] To'lov integratsiyasi (Payme / Click) — keyingi bosqich
- [ ] Push bildirishnomalar
- [ ] PostgreSQL + Flyway (prod)
- [ ] POS tizimi bilan mahsulot/qoldiq sinxronizatsiyasi

Batafsil reja: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
