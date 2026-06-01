# Architecture — Barakat Market Mobile

## Yuqori darajadagi ko'rinish

```
┌──────────────────────┐        HTTPS / REST (JSON)        ┌───────────────────────┐
│   Flutter app        │ ───────────────────────────────► │  Spring Boot backend   │
│   (Android + iOS)    │ ◄─────────────────────────────── │  (mobile API, :8090)   │
│                      │            JWT bearer             │                        │
│  Riverpod state      │                                   │  Spring Security + JWT │
│  Dio HTTP client     │                                   │  JPA / Hibernate       │
│  go_router           │                                   │  H2 (dev) / PG (prod)  │
└──────────────────────┘                                   └───────────┬───────────┘
                                                                        │ (kelajakda)
                                                            ┌───────────▼───────────┐
                                                            │  Barakat POS backend   │
                                                            │  (mahsulot/qoldiq DB)  │
                                                            └────────────────────────┘
```

Mobil backend MVP bosqichida **mustaqil** ishlaydi (o'z bazasi, namuna mahsulotlar).
Keyingi bosqichda mavjud POS backend (`uz.barakat.market`) bilan mahsulot va
qoldiq sinxronizatsiyasi qo'shiladi.

## Backend paketlari (`uz.barakat.mobile`)

```
config/        Security, CORS, JWT
domain/        JPA entity'lar (Customer, Category, Product, Cart, Order, ...)
repository/    Spring Data JPA interfeyslari
dto/           Request/Response record'lar
service/       Biznes mantiq (Auth, Catalog, Cart, Order, Address)
controller/    REST endpoint'lar
bootstrap/     DataSeeder (dev namuna ma'lumotlari)
exception/     Global xato ishlovchi
```

## Flutter ilova qatlamlari (`app/lib`)

```
core/
  config/      AppConfig (API base url, konstantalar)
  theme/       Brend ranglar, tipografiya, ThemeData
  network/     Dio klient, interceptor (JWT, log), API exception
  router/      go_router konfiguratsiyasi
  storage/     Token saqlash (secure storage)
features/
  auth/        phone login, OTP, profil
  home/        bosh sahifa (bannerlar, kategoriyalar)
  catalog/     kategoriya/mahsulot ro'yxati, qidiruv
  product/     mahsulot detali
  cart/        savatcha
  checkout/    buyurtma rasmiylashtirish
  orders/      buyurtmalar tarixi va holat
  profile/     profil, manzillar
shared/
  models/      DTO modellari (json_serializable)
  widgets/     qayta ishlatiladigan UI komponentlar
```

Har bir feature: `data/` (repository + API), `application/` (Riverpod provider/notifier),
`presentation/` (screen + widget).

## Ma'lumotlar modeli (asosiy)

```
Customer 1───* Address
Customer 1───1 Cart 1───* CartItem *───1 Product
Customer 1───* Order 1───* OrderItem
Category 1───* Product
OtpCode (telefon bo'yicha vaqtinchalik kod)
```

## Xavfsizlik

- OTP: telefon raqamga 4 xonali kod (dev rejimida javobda/logda ko'rinadi;
  prod'da SMS provayder — Eskiz/Play Mobile orqali).
- JWT: HS256, `SAVDOPRO_JWT_SECRET` env yoki `app.jwt.secret`. Dev fallback bor.
- Public endpointlar: `/health`, `/auth/**`, `/categories`, `/products/**`, `/banners`.
- Qolgan hammasi JWT talab qiladi.

## Keyingi bosqichlar

1. To'lov: Payme/Click integratsiyasi (server-to-server + deep link).
2. Push: Firebase Cloud Messaging — buyurtma holati o'zgarganda.
3. Real-time kuzatuv: kuryer joylashuvi (WebSocket/STOMP — POS backendda allaqachon bor).
4. POS sinxronizatsiyasi: mahsulot, narx, qoldiq.
5. PostgreSQL + Flyway migratsiyalar (prod).
```
