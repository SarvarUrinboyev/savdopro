# API Contract — Barakat Market Mobile Backend

Base URL (dev): `http://localhost:8090/api`
Auth: `Authorization: Bearer <JWT>` (OTP tasdiqlangach beriladi).
Barcha javoblar JSON. Pul birligi — **tiyin emas, butun so'm** (UZS, integer).

---

## Auth

### `POST /auth/request-otp`
Telefon raqamga SMS kod yuborish (dev rejimida kod logga yoziladi va javobda qaytadi).
```json
// request
{ "phone": "+998901234567" }
// response 200
{ "phone": "+998901234567", "expiresInSeconds": 120, "devCode": "1234" }
```

### `POST /auth/verify-otp`
Kodni tasdiqlash → JWT token.
```json
// request
{ "phone": "+998901234567", "code": "1234" }
// response 200
{ "token": "<jwt>", "customer": { "id": 1, "phone": "+998901234567", "name": null } }
```

### `GET /auth/me`  *(auth)*
Joriy mijoz profili.

### `PATCH /auth/me`  *(auth)*
```json
{ "name": "Sarvar", "email": "..." }
```

---

## Catalog  *(public)*

### `GET /categories`
```json
[ { "id": 1, "name": "Sut mahsulotlari", "slug": "sut", "iconUrl": null, "productCount": 12 } ]
```

### `GET /products`
Query: `categoryId`, `q` (qidiruv), `page` (0-based), `size` (default 20), `sort` (`popular|price_asc|price_desc|new`).
```json
{
  "content": [
    { "id": 10, "name": "Sut 2.5% 1L", "price": 12000, "oldPrice": 14000,
      "unit": "dona", "imageUrl": null, "categoryId": 1, "inStock": true, "discountPercent": 14 }
  ],
  "page": 0, "size": 20, "totalElements": 120, "totalPages": 6
}
```

### `GET /products/{id}`
Bitta mahsulotning to'liq ma'lumoti (`description`, `images[]` qo'shiladi).

### `GET /banners`  *(public)*
Bosh sahifa bannerlari.

---

## Cart  *(auth)*

### `GET /cart`
```json
{ "items": [ { "productId": 10, "name": "...", "price": 12000, "quantity": 2, "lineTotal": 24000, "imageUrl": null } ],
  "itemCount": 2, "subtotal": 24000, "deliveryFee": 15000, "total": 39000 }
```

### `PUT /cart/items/{productId}`  — miqdorni o'rnatish (0 = o'chirish)
```json
{ "quantity": 3 }
```

### `DELETE /cart`  — savatni tozalash

---

## Addresses  *(auth)*

- `GET /addresses` — ro'yxat
- `POST /addresses` — `{ "label": "Uy", "addressLine": "...", "lat": 41.3, "lng": 69.2, "comment": "..." }`
- `DELETE /addresses/{id}`

---

## Orders  *(auth)*

### `POST /orders` — savatdan buyurtma yaratish
```json
// request
{ "addressId": 5, "deliveryType": "DELIVERY", "paymentMethod": "CASH",
  "deliverySlot": "2026-06-01T15:00:00", "comment": "..." }
// response 201
{ "id": 100, "status": "NEW", "total": 39000, "createdAt": "...", "items": [ ... ] }
```

### `GET /orders` — buyurtmalar tarixi (auth)
### `GET /orders/{id}` — bitta buyurtma + holat

**Order status oqimi:** `NEW → CONFIRMED → ASSEMBLING → ON_THE_WAY → DELIVERED`
(yoki `CANCELLED`).

**deliveryType:** `DELIVERY` | `PICKUP`
**paymentMethod:** `CASH` | `CARD_ON_DELIVERY` | `PAYME` | `CLICK` (oxirgi ikkisi keyingi bosqichda)

---

## Health  *(public)*
### `GET /health` → `{ "status": "UP", "service": "barakat-mobile", "time": "..." }`

---

## Xato formati
```json
{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "...", "path": "/api/..." }
```
