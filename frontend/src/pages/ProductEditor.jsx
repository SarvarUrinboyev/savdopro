import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { CategoryApi, ExchangeRateApi, ProductApi } from '../api/endpoints.js';
import { ConfirmDialog } from '../components/Modal.jsx';
import { useToast } from '../components/Toast.jsx';
import { CurrencyToggle, Loader } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { useStickyState } from '../hooks/useStickyState.js';
import { formatDateTime, money, usd } from '../lib/format.js';

const REASONS = [
  { value: 'DELIVERY', label: 'Yangi yetkazib berish' },
  { value: 'SALE', label: 'Sotuv' },
  { value: 'RETURN', label: 'Qaytarish' },
  { value: 'CORRECTION', label: 'Tuzatish (inventarizatsiya)' },
  { value: 'WRITEOFF', label: "Brak / yo'qotish" },
];

const REASON_LABEL = {
  INITIAL: "Boshlang'ich qoldiq",
  DELIVERY: 'Yangi yetkazib berish',
  SALE: 'Sotuv',
  RETURN: 'Qaytarish',
  CORRECTION: 'Tuzatish',
  WRITEOFF: "Brak / yo'qotish",
};

// An IMEI is a 15-digit number — strip anything that isn't a digit and cap at
// 15 so the field can never hold letters or an over-long value.
const imeiClean = (v) => (v || '').replace(/\D/g, '').slice(0, 15);

// A category whose name hints at phones flips the "is a phone" switch on, so the
// operator doesn't have to remember to reveal the IMEI fields.
const PHONE_HINTS = ['telefon', 'smartfon', 'smartphone', 'phone', 'iphone',
  'samsung', 'xiaomi', 'redmi', 'oppo', 'vivo', 'tecno', 'infinix', 'realme',
  'honor', 'nokia', 'pixel', 'huawei'];
const looksLikePhone = (s) =>
  PHONE_HINTS.some((h) => (s || '').toLowerCase().includes(h));

export function ProductEditor() {
  const { id } = useParams();
  const isNew = !id;
  const { data, loading, error, reload } = useApi(
    () => Promise.all([
      isNew ? Promise.resolve(null) : ProductApi.get(id),
      CategoryApi.list(),
      isNew ? Promise.resolve([]) : ProductApi.movements(id),
      // CBU USD->UZS rate: lets the operator type prices in so'm. Optional —
      // offline the form simply stays USD-only.
      ExchangeRateApi.get().catch(() => null),
    ]),
    [id],
  );

  return (
    <Loader loading={loading} error={error} onRetry={reload}>
      {/* In edit mode wait for the product itself: during the
          new -> saved navigation useApi briefly holds the stale
          "new page" data ([null, ...]) and Editor must not run
          with a null product. */}
      {data && (isNew || data[0]) && (
        <Editor
          key={isNew ? 'new' : id}
          isNew={isNew}
          product={data[0]}
          categories={data[1]}
          movements={data[2]}
          rate={data[3]}
          reloadAll={reload}
        />
      )}
    </Loader>
  );
}

function Editor({ isNew, product, categories, movements, rate, reloadAll }) {
  const t = useT();
  const navigate = useNavigate();
  const toast = useToast();

  const [name, setName] = useState(product?.name ?? '');
  const [barcode, setBarcode] = useState(product?.barcode ?? '');
  const [imei1, setImei1] = useState(imeiClean(product?.imei1 ?? ''));
  const [imei2, setImei2] = useState(imeiClean(product?.imei2 ?? ''));
  // IMEI only matters for phones — hidden by default, pre-enabled when editing a
  // product that already carries one.
  const [isPhone, setIsPhone] = useState(Boolean(product?.imei1 || product?.imei2));
  // When true, the POS asks the cashier for each unit's IMEI/serial at sale time
  // and records it against the customer (for warranty / debt / Knox Guard).
  const [requiresImei, setRequiresImei] = useState(product?.requiresImei ?? false);

  // ----- price entry currency (USD / so'm) -----
  // Prices are STORED in USD (reports, margins and the POS all assume it);
  // the toggle only changes what the operator types. So'm input is converted
  // at the CBU rate on save. Without a rate (offline) the form is USD-only.
  const usdRate = rate?.available && Number(rate.rate) > 0 ? Number(rate.rate) : null;
  const [priceCurrency, setPriceCurrency] = useStickyState('barakat.cur.product', 'USD');
  const curr = usdRate ? priceCurrency : 'USD';
  const uzsToUsd = (n) => Math.round((n / usdRate) * 100) / 100;
  // Stored USD -> what the input should show in the sticky currency.
  const initPrice = (v) => {
    if (v == null || v === '') return '';
    return (usdRate && priceCurrency === 'UZS')
      ? String(Math.round(Number(v) * usdRate))
      : String(v);
  };
  const [purchasePrice, setPurchasePrice] = useState(() => initPrice(product?.purchasePrice));
  const [salePrice, setSalePrice] = useState(() => initPrice(product?.salePrice));

  const switchCurrency = (next) => {
    if (next === curr) return;
    if (!usdRate) {
      toast.error(t("Dollar kursi yuklanmadi — narxni hozircha USDda kiriting"));
      return;
    }
    // Keep what the operator already typed: convert it to the new currency.
    const conv = (v) => {
      const n = Number(v);
      if (v === '' || v == null || !Number.isFinite(n)) return '';
      return next === 'UZS' ? String(Math.round(n * usdRate)) : String(uzsToUsd(n));
    };
    setPurchasePrice(conv);
    setSalePrice(conv);
    setPriceCurrency(next);
  };

  /** "≈ $1.24" under a so'm input (and vice versa) so the stored value is visible. */
  const equiv = (v) => {
    const n = Number(v);
    if (!usdRate || !Number.isFinite(n) || n <= 0) return null;
    return curr === 'UZS'
      ? `≈ ${usd(uzsToUsd(n))}`
      : `≈ ${money(Math.round(n * usdRate))} so'm`;
  };
  const [categoryId, setCategoryId] = useState(product?.categoryId ?? '');
  const [description, setDescription] = useState(product?.description ?? '');
  const [threshold, setThreshold] = useState(product?.lowStockThreshold ?? 0);
  const [mxikCode, setMxikCode] = useState(product?.mxikCode ?? '');
  const [vatRate, setVatRate] = useState(
    product?.vatRate != null ? String(product.vatRate) : '',
  );
  const [unit, setUnit] = useState(product?.unit ?? 'dona');
  const [expiryDate, setExpiryDate] = useState(product?.expiryDate ?? '');
  const [initialQty, setInitialQty] = useState('');
  const [busy, setBusy] = useState(false);

  // Local copy so creating a category inline doesn't reload the whole form.
  const [cats, setCats] = useState(categories ?? []);

  const createCategory = async () => {
    const name = window.prompt(t('Yangi toifa nomi:'));
    if (!name || !name.trim()) {
      return;
    }
    try {
      const created = await CategoryApi.create({ name: name.trim() });
      setCats((prev) => [...prev, created]
        .sort((a, b) => a.name.localeCompare(b.name)));
      setCategoryId(String(created.id));
      toast.success(`${t('Toifa qo‘shildi')}: ${created.name}`);
    } catch (err) {
      toast.error(err.message);
    }
  };

  const [adjustDelta, setAdjustDelta] = useState('');
  const [adjustReason, setAdjustReason] = useState('DELIVERY');
  const [stockBusy, setStockBusy] = useState(false);

  const [confirmDelete, setConfirmDelete] = useState(false);

  const save = async () => {
    if (!name.trim()) {
      toast.error(t('Mahsulot nomi kiritilishi shart'));
      return;
    }
    if (isPhone) {
      // Only enforce the 15-digit rule on a value the operator actually typed —
      // a legacy/imported IMEI of another length must not block an unrelated edit.
      const origI1 = imeiClean(product?.imei1 ?? '');
      const origI2 = imeiClean(product?.imei2 ?? '');
      if (imei1 && imei1 !== origI1 && imei1.length !== 15) {
        toast.error(t("IMEI 1 to'liq 15 raqamdan iborat bo'lishi kerak"));
        return;
      }
      if (imei2 && imei2 !== origI2 && imei2.length !== 15) {
        toast.error(t("IMEI 2 to'liq 15 raqamdan iborat bo'lishi kerak"));
        return;
      }
    }
    const buyN = Number(purchasePrice) || 0;
    const sellN = Number(salePrice) || 0;
    // Validate what will actually be STORED, not just what was typed: a tiny
    // so'm price (under ~half a US cent) rounds to $0.00 and would ring up free.
    const buyUsd = curr === 'UZS' ? uzsToUsd(buyN) : buyN;
    const sellUsd = curr === 'UZS' ? uzsToUsd(sellN) : sellN;
    if (sellN <= 0 || sellUsd <= 0) {
      toast.error(t("Sotilish narxi 0 dan katta bo'lishi kerak"));
      return;
    }
    if (buyN > 0 && buyUsd <= 0) {
      toast.error(t("Kelish narxi juda kichik — USDga aylantirilganda $0 bo'lib qoladi"));
      return;
    }
    if (buyUsd > 0 && sellUsd < buyUsd) {
      // Allow but warn — sometimes sold under cost (sale clearance).
      toast.info(t("Diqqat: sotilish narxi kelish narxidan past"));
    }
    const body = {
      name: name.trim(),
      barcode: barcode.trim() || null,
      imei1: isPhone ? (imei1.trim() || null) : null,
      imei2: isPhone ? (imei2.trim() || null) : null,
      purchasePrice: buyUsd,
      salePrice: sellUsd,
      categoryId: categoryId ? Number(categoryId) : null,
      description: description.trim() || null,
      lowStockThreshold: Number(threshold) || 0,
      mxikCode: mxikCode.trim() || null,
      vatRate: vatRate === '' ? null : Number(vatRate),
      unit: unit || 'dona',
      expiryDate: expiryDate || null,
      requiresImei,
    };
    if (isNew) {
      body.quantity = Number(initialQty) || 0;
    }
    setBusy(true);
    try {
      if (isNew) {
        const created = await ProductApi.create(body);
        toast.success(t("Mahsulot qo'shildi"));
        navigate(`/warehouse/${created.id}`);
      } else {
        await ProductApi.update(product.id, body);
        toast.success(t('Saqlandi'));
        reloadAll();
        setBusy(false);
      }
    } catch (err) {
      toast.error(err.message);
      setBusy(false);
    }
  };

  const applyStock = async () => {
    const delta = parseInt(adjustDelta, 10);
    if (!delta) {
      toast.error(t("Miqdorni kiriting (masalan +10 yoki -3)"));
      return;
    }
    setStockBusy(true);
    try {
      await ProductApi.adjust(product.id, { delta, reason: adjustReason, note: null });
      toast.success(t('Ombor miqdori yangilandi'));
      setAdjustDelta('');
      reloadAll();
    } catch (err) {
      toast.error(err.message);
    }
    setStockBusy(false);
  };

  const remove = async () => {
    try {
      await ProductApi.remove(product.id);
      toast.success(t("Mahsulot o'chirildi"));
      navigate('/warehouse');
    } catch (err) {
      toast.error(err.message);
    }
  };

  return (
    <>
      <div className="page-head">
        <div>
          <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: '.08em',
                         color: 'var(--text-faint)' }}>
            <Link to="/warehouse" style={{ color: 'inherit' }}>{t('OMBOR')}</Link>
            {'  /  '}
            <Link to="/warehouse" style={{ color: 'inherit' }}>{t('MAHSULOTLAR')}</Link>
            {'  /  '}
            {isNew ? t('YANGI') : t('TAHRIRLASH')}
          </div>
          <h1>{isNew ? t('Yangi mahsulot') : product.name}</h1>
        </div>
        <div className="actions">
          <button className="btn btn-ghost" onClick={() => navigate('/warehouse')}>
            {t('Orqaga')}
          </button>
          <button className="btn btn-primary" onClick={save} disabled={busy}>
            {busy ? t('Saqlanmoqda...') : t('Saqlash')}
          </button>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1.7fr 1fr', gap: 16 }}>
        <div>
          <div className="card">
            <div className="card-head"><h2>{t("Asosiy ma'lumotlar")}</h2></div>
            <div className="card-pad">
              <div className="field">
                <label>{t('Nomi *')}</label>
                <input className="input" value={name}
                       onChange={(e) => setName(e.target.value)} />
              </div>
              <div className="field">
                <label>{t('Narx valyutasi')}</label>
                <CurrencyToggle value={curr} onChange={switchCurrency} />
                <div className="field-hint">
                  {usdRate
                    ? <>1 USD = {money(Math.round(usdRate))} so'm ({t('Markaziy bank')})
                        {curr === 'UZS' && <> · {t("so'mda kiritilgan narx kurs bo'yicha USDga aylantirib saqlanadi")}</>}</>
                    : t('Dollar kursi yuklanmadi — narxlar hozircha faqat USDda kiritiladi')}
                </div>
              </div>
              <div className="form-row">
                <div className="field">
                  <label>{curr === 'UZS' ? t("Kelish narxi (so'm) *") : t('Kelish narxi (USD) *')}</label>
                  <input className="input" type="number" value={purchasePrice}
                         onChange={(e) => setPurchasePrice(e.target.value)} placeholder="0" />
                  {equiv(purchasePrice) && (
                    <div className="field-hint" style={{ fontWeight: 600 }}>{equiv(purchasePrice)}</div>
                  )}
                </div>
                <div className="field">
                  <label>{curr === 'UZS' ? t("Sotilish narxi (so'm) *") : t('Sotilish narxi (USD) *')}</label>
                  <input className="input" type="number" value={salePrice}
                         onChange={(e) => setSalePrice(e.target.value)} placeholder="0" />
                  {equiv(salePrice) && (
                    <div className="field-hint" style={{ fontWeight: 600 }}>{equiv(salePrice)}</div>
                  )}
                </div>
              </div>
              <div className="field">
                <label>{t('Shtrix kod')}</label>
                <input className="input" value={barcode}
                       onChange={(e) => setBarcode(e.target.value)}
                       placeholder={t("Skanerdan yoki qo'lda — masalan 4780000000017")} />
              </div>
              <div className="field">
                <label style={{ display: 'flex', alignItems: 'center', gap: 8,
                                cursor: 'pointer', userSelect: 'none' }}>
                  <input type="checkbox" checked={isPhone}
                         onChange={(e) => setIsPhone(e.target.checked)}
                         style={{ width: 16, height: 16, accentColor: 'var(--green)' }} />
                  📱 {t('Telefon (IMEI raqami bor)')}
                </label>
                <div className="field-hint">
                  {t('Faqat smartfon/telefon uchun yoqing — IMEI maydonlari chiqadi. Boshqa tovarlar uchun shart emas.')}
                </div>
              </div>
              {isPhone && (
                <div className="form-row">
                  <ImeiField label={t('IMEI 1')} value={imei1} onChange={setImei1}
                             placeholder={t('15 raqam, masalan 353915110000001')} t={t} />
                  <ImeiField label={t('IMEI 2 (ixtiyoriy)')} value={imei2} onChange={setImei2}
                             placeholder={t('ikkinchi SIM')} t={t} />
                </div>
              )}
              <div className="field">
                <label style={{ display: 'flex', alignItems: 'center', gap: 8,
                                cursor: 'pointer', userSelect: 'none' }}>
                  <input type="checkbox" checked={requiresImei}
                         onChange={(e) => setRequiresImei(e.target.checked)}
                         style={{ width: 16, height: 16, accentColor: 'var(--green)' }} />
                  🔒 {t('Sotuvda IMEI talab qilinsin')}
                </label>
                <div className="field-hint">
                  {t('Elektronika/smartfonlar uchun yoqing — sotilganda har bir donaning IMEI/seriya raqami so‘raladi va mijozga bog‘lab saqlanadi (Qurilmalar bo‘limida ko‘rinadi).')}
                </div>
              </div>
              <div className="field">
                <label>{t('Toifa')}</label>
                <select className="select" value={categoryId}
                        onChange={(e) => {
                          const v = e.target.value;
                          if (v === '__new__') { createCategory(); return; }
                          setCategoryId(v);
                          const cat = cats.find((c) => String(c.id) === String(v));
                          if (cat && looksLikePhone(cat.name)) setIsPhone(true);
                        }}>
                  <option value="">{t('Tanlanmagan')}</option>
                  {cats.map((c) => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                  <option value="__new__">➕ {t('Yangi toifa qo‘shish…')}</option>
                </select>
              </div>
              {isNew && (
                <div className="field">
                  <label>{t("Boshlang'ich miqdor (dona)")}</label>
                  <input className="input" type="number" value={initialQty}
                         onChange={(e) => setInitialQty(e.target.value)} placeholder="0" />
                </div>
              )}
              <div className="field">
                <label>{t('Tavsif')}</label>
                <textarea className="input" value={description}
                          onChange={(e) => setDescription(e.target.value)}
                          placeholder={t('Mahsulot xususiyatlarini yozing...')} />
              </div>
            </div>
          </div>

          <div className="card mt-16">
            <div className="card-head"><h2>{t("Soliq ma'lumotlari")}</h2></div>
            <div className="card-pad">
              <p className="muted" style={{ marginBottom: 12, fontSize: 13 }}>
                {t("Elektron faktura va onlayn-kassa uchun (ixtiyoriy). Bu ma'lumotlar hech qayerga yuborilmaydi — integratsiya yoqilmaguncha shunchaki saqlanadi.")}
              </p>
              <div className="field">
                <label>{t('IKPU / MXIK kodi')}</label>
                <input className="input" value={mxikCode}
                       onChange={(e) => setMxikCode(e.target.value)}
                       placeholder={t('Milliy katalog kodi')} />
              </div>
              <div className="form-row">
                <div className="field">
                  <label>{t('QQS stavkasi')}</label>
                  <select className="select" value={vatRate}
                          onChange={(e) => setVatRate(e.target.value)}>
                    <option value="">{t('Belgilanmagan')}</option>
                    <option value="0">0%</option>
                    <option value="12">12%</option>
                  </select>
                </div>
                <div className="field">
                  <label>{t("O'lchov birligi")}</label>
                  <select className="select" value={unit}
                          onChange={(e) => setUnit(e.target.value)}>
                    <option value="dona">{t('dona')}</option>
                    <option value="kg">{t('kg')}</option>
                    <option value="litr">{t('litr')}</option>
                    <option value="metr">{t('metr')}</option>
                    <option value="quti">{t('quti')}</option>
                    <option value="to'plam">{t("to'plam")}</option>
                  </select>
                </div>
                <div className="field">
                  <label>{t('Yaroqlilik muddati (ixtiyoriy)')}</label>
                  <input className="input" type="date" value={expiryDate || ''}
                         onChange={(e) => setExpiryDate(e.target.value)} />
                </div>
              </div>
            </div>
          </div>

          {!isNew && (
            <button
              className="btn btn-ghost btn-sm mt-16"
              style={{ color: 'var(--red)' }}
              onClick={() => setConfirmDelete(true)}
            >
              🗑 {t("Mahsulotni o'chirish")}
            </button>
          )}
        </div>

        <div className="list-stack">
          <div className="card">
            <div className="card-head"><h2>{t('Ogohlantirish chegarasi')}</h2></div>
            <div className="card-pad">
              <div className="field" style={{ margin: 0 }}>
                <label>{t('Past stok chegarasi')}</label>
                <input className="input" type="number" value={threshold}
                       onChange={(e) => setThreshold(e.target.value)} />
                <div className="field-hint">
                  {t('Miqdor shu chegaraga tushganda mahsulot "Kam qoldi" deb belgilanadi. 0 = ogohlantirish o\'chirilgan.')}
                </div>
              </div>
            </div>
          </div>

          {!isNew && (
            <div className="card">
              <div className="card-head">
                <h2>{t("Ombor o'zgarishi")}</h2>
                <span className="hint">{t('Qoldiq:')} {product.quantity} {t('dona')}</span>
              </div>
              <div className="card-pad">
                <div className="field">
                  <label>{t("O'zgarish (+ qo'shish / − ayirish)")}</label>
                  <input className="input" type="number" value={adjustDelta}
                         onChange={(e) => setAdjustDelta(e.target.value)}
                         placeholder={t('+10 yoki -3')} />
                </div>
                <div className="field">
                  <label>{t('Sabab')}</label>
                  <select className="select" value={adjustReason}
                          onChange={(e) => setAdjustReason(e.target.value)}>
                    {REASONS.map((r) => (
                      <option key={r.value} value={r.value}>{t(r.label)}</option>
                    ))}
                  </select>
                </div>
                <button className="btn btn-primary btn-block" onClick={applyStock}
                        disabled={stockBusy}>
                  {stockBusy ? t('Saqlanmoqda...') : t("Qo'llash")}
                </button>
              </div>
            </div>
          )}

          {!isNew && (
            <div className="card">
              <div className="card-head"><h2>{t('Ombor harakatlari')}</h2></div>
              <div className="card-pad">
                {movements.length === 0 ? (
                  <div className="faint" style={{ fontSize: 13 }}>
                    {t("Hech qanday harakat yo'q.")}
                  </div>
                ) : (
                  movements.map((m) => (
                    <div
                      key={m.id}
                      style={{
                        padding: '8px 0', fontSize: 13,
                        borderBottom: '1px solid var(--border)',
                      }}
                    >
                      <div className="flex-between">
                        <b className={m.delta >= 0 ? 'amount-pos' : 'amount-neg'}>
                          {m.delta >= 0 ? '+' : ''}{m.delta} {t('dona')}
                        </b>
                        <span className="faint">→ {m.resultingQuantity} {t('dona')}</span>
                      </div>
                      <div className="faint" style={{ fontSize: 11, marginTop: 2 }}>
                        {t(REASON_LABEL[m.reason] || m.reason)} &middot; {formatDateTime(m.createdAt)}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      {confirmDelete && (
        <ConfirmDialog
          title={t("Mahsulotni o'chirish")}
          message={`"${product.name}" ${t("mahsulotini ombordan o'chirmoqchimisiz?")}`}
          confirmLabel={t("O'chirish")}
          onConfirm={remove}
          onCancel={() => setConfirmDelete(false)}
        />
      )}
    </>
  );
}

/** A single IMEI input: digits only, capped at 15, with a live 0/15 counter. */
function ImeiField({ label, value, onChange, placeholder, t }) {
  const len = value.length;
  return (
    <div className="field">
      <label>{label}</label>
      <input className="input mono" inputMode="numeric" maxLength={15}
             value={value} onChange={(e) => onChange(imeiClean(e.target.value))}
             placeholder={placeholder} />
      {len > 0 && (
        <div className="field-hint"
             style={{ color: len === 15 ? '#16a34a' : '#f59e0b', fontWeight: 600 }}>
          {len === 15 ? `✓ ${t("15 ta raqam — to'g'ri")}` : `${len}/15 ${t('raqam')}`}
        </div>
      )}
    </div>
  );
}
