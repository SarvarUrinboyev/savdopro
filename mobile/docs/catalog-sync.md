# POS → Mobil katalog sinxroni

Mobil ilova katalogi endi SavdoPRO POS omboridan avtomatik to'ladi —
`CatalogSyncService` har 30 daqiqada Public API'dan mahsulotlarni tortadi.

## Yoqish

1. **POS tomonida API kalit yarating**: SavdoPRO → Integratsiyalar →
   yangi API kalit, scope: `catalog:read`. Kalit bir marta ko'rsatiladi.
2. **Mobil server env** (`mobile/server` ishga tushishida):

```bash
CATALOG_SYNC_ENABLED=true
CATALOG_SYNC_BASE_URL=https://<savdopro-host>/api/v1
CATALOG_SYNC_API_KEY=sk_live_...
CATALOG_SYNC_UZS_PER_USD=12800   # POS narxlari USD -> so'm kursi
CATALOG_SYNC_MARKUP=1.0          # B2C ustama (masalan 1.15 = +15%)
# CATALOG_SYNC_CRON=0 */30 * * * *
```

3. Logda tasdiq: `Catalog sync: N yangi, M yangilandi, K o'chirildi`.

## Qoidalar

| Narsa | Xatti-harakat |
|---|---|
| Upsert kaliti | `source_product_id` (POS mahsulot IDsi) |
| Qo'lda kiritilgan mahsulotlar | `source_product_id=null` — sinxron TEGMAYDI |
| Narx | `USD × kurs × markup`, 100 so'mga yaxlit |
| Rasm / tavsif / kategoriya | mobil tomonda kuratsiya — yangilanmaydi |
| Yangi mahsulot | "POS import" kategoriyasiga tushadi (keyin ko'chiring) |
| POS'dan o'chgan mahsulot | `active=false` (hech qachon o'chirib tashlanmaydi) |
| Qoldiq | `stockQty` har sinxronda POS bilan tenglashadi |

## Keyingi bosqich (hali qilinmagan): mobil buyurtma to'lovi

Hozir mobil buyurtmalar naqd/kuryer hisobida. Click/Payme bilan onlayn
to'lash uchun:
1. POS backend'dagi mavjud `payment/ClickController` + `PaymeController`
   naqshini mobil serverga ko'chirish (yoki mobil buyurtmani POS'ga webhook
   bilan o'tkazib, to'lovni POS orqali qabul qilish).
2. Merchant kredensiallar (billing uchun olinganlari bilan bir xil emas —
   alohida kassa/servis ID kerak bo'lishi mumkin).
3. Flutter'da to'lov ekrani: checkout → `checkoutUrl` redirect → natija
   deep-link.

Do'kon nashri (Play Market) bo'yicha: `mobile/README.md` + Play Console
hisob talab qilinadi — bu qo'lda bajariladigan qadam.
