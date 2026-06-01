# Barakat Market — Mijozlar ilovasi (Flutter)

Mijozlar uchun xarid va yetkazib berish ilovasi. Flutter + Riverpod + Dio + go_router.

## Arxitektura

```
lib/
├── main.dart                      # Kirish nuqtasi (ProviderScope)
├── app/
│   ├── app.dart                   # MaterialApp.router + tema
│   ├── router.dart                # go_router: auth redirect + bottom-nav shell
│   ├── scaffold_with_nav.dart     # 5 tabli bottom navigation (savat badge bilan)
│   └── splash_screen.dart         # Token tekshirilayotgan paytdagi ekran
├── core/
│   ├── config/app_config.dart     # API manzili, konstantalar
│   ├── network/
│   │   ├── api_client.dart         # Dio + JWT interceptor + 401 handling
│   │   └── api_exception.dart      # Foydalanuvchi tilidagi xatolar
│   ├── providers.dart              # tokenStorage, apiClient provayderlari
│   ├── storage/token_storage.dart  # JWT xavfsiz saqlash (Keystore/Keychain)
│   ├── theme/                      # Brend ranglari + Material 3 tema
│   └── utils/formatters.dart       # Pul / sana format
├── features/
│   ├── auth/                       # OTP login (telefon → kod → JWT) + sessiya
│   ├── catalog/                    # Bosh sahifa, kategoriya, mahsulot, qidiruv
│   ├── cart/                       # Mahalliy (client-side) savatcha
│   ├── orders/                     # Checkout, buyurtmalar ro'yxati, status kuzatuvchi
│   ├── address/                    # Yetkazib berish manzillari (CRUD)
│   └── profile/                    # Profil + sozlamalar
└── shared/
    ├── models/                     # Manual fromJson modellar (codegen kerak emas)
    └── widgets/                    # ProductCard, QuantityStepper, AsyncValueView, ...
```

**State management:** Riverpod (StateNotifier sessiya/savat/paginatsiya uchun,
FutureProvider read-only ma'lumotlar uchun).

**Sessiya oqimi:** ilova ochilganda `AuthController` saqlangan JWT'ni `/auth/me`
bilan tekshiradi → router `splash → login → home` ga yo'naltiradi. 401 yuz
berganda avtomatik logout.

**Savatcha:** mahalliy (qurilmada) saqlanadi, checkout paytida serverga
yuboriladi. Yetkazib berish narxi UI tahmini — haqiqiy summa serverda.

## Ishga tushirish

```bash
# Platforma papkalarini yaratish (android/ios/ hozircha yo'q — faqat lib/ + pubspec):
flutter create .

flutter pub get

# Backend Android emulyatorda 10.0.2.2:8090 da deb taxmin qilinadi.
# Boshqa manzil uchun:
flutter run --dart-define=API_BASE_URL=http://<IP>:8090/api
```

## ⚠️ Backend kontrakti (MUHIM)

Bu ilova quyidagi **mijoz-storefront** REST kontraktini kutadi:

| Endpoint | Metod | Tavsif |
|----------|-------|--------|
| `/auth/request-otp` | POST | `{phone}` → OTP yuborish |
| `/auth/verify-otp` | POST | `{phone, code}` → `{token, customer}` |
| `/auth/me` | GET / PATCH | Profil olish / yangilash |
| `/categories` | GET | Kategoriyalar |
| `/banners` | GET | Bosh sahifa bannerlari |
| `/products?page&size&categoryId&search` | GET | Sahifalangan mahsulotlar |
| `/products/{id}` | GET | Mahsulot tafsiloti |
| `/orders` | GET / POST | Buyurtmalar ro'yxati / yaratish |
| `/orders/{id}` | GET | Buyurtma tafsiloti |
| `/addresses` | GET/POST/PUT/DELETE | Manzillar |

**Joriy Spring Boot backend bu endpoint'larni hali amalga oshirmagan.**
Mavjud `/api/*` controller'lar POS/admin yo'naltirilgan (supplier orders,
purchasePrice/margin, barcode) — mijoz-storefront shaklida emas. Bu ilovani
real ma'lumot bilan ishlatish uchun backend'da yuqoridagi kontraktni
amalga oshiruvchi customer-storefront API qatlami qo'shilishi kerak
(yoki Postman mock orqali). API shakllari `lib/shared/models/*.dart`
`fromJson` larida aniq belgilangan.

## Eslatma — moslik

`Color.withOpacity()` ishlatilgan (Flutter 3.22+ da xatosiz; 3.27+ da faqat
deprecation ogohlantirishi). `analysis_options.yaml` buni `warning` darajasida
qoldiradi — build to'xtamaydi.
