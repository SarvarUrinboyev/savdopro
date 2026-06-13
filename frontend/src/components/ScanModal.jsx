import { useEffect, useRef, useState } from 'react';
import { ExchangeRateApi, ProductApi } from '../api/endpoints.js';
import { normalizeBarcode } from '../lib/barcode.js';
import { getSavedProxyUrl, lookupCatalog, setSavedProxyUrl } from '../lib/catalog.js';
import { useT } from '../context/Settings.jsx';
import { Modal } from './Modal.jsx';
import { useToast } from './Toast.jsx';
import { CurrencyToggle } from './ui.jsx';
import { useStickyState } from '../hooks/useStickyState.js';
import { money, usd } from '../lib/format.js';

// Sentinel category value: "create the catalogue's suggested category on save".
const NEW_CATEGORY = '__suggested__';

/**
 * Barcode scanner dialog. A USB scanner behaves like a keyboard: it "types" the
 * code into the focused input and presses Enter.
 *
 *  • A code already in the warehouse -> "Kirim" form: the name and category are
 *    shown ready, the cashier types only how many units arrived.
 *  • An unknown code -> new-product form, pre-filled from the national catalogue
 *    (tasnif.soliq.uz) when the GTIN is recognised — so usually only the
 *    quantity is left to type.
 *
 * GS1 DataMatrix marking codes ("ASL BELGISI") carry a unique serial per unit;
 * the backend reduces them to the product's GTIN, so re-scanning any unit of the
 * same product is recognised instead of looking "new" every time.
 */
export function ScanModal({ categories, onClose, onChanged }) {
  const inputRef = useRef(null);
  const [code, setCode] = useState('');
  const [log, setLog] = useState([]);
  const [busy, setBusy] = useState(false);
  const toast = useToast();
  const t = useT();

  // 'scan' (idle), 'restock' (known product) or 'new' (unknown product).
  const [mode, setMode] = useState('scan');
  const [barcode, setBarcode] = useState('');   // canonical code to store
  const [searching, setSearching] = useState(false); // catalogue lookup in flight

  // Optional: the in-Uzbekistan relay URL for the national MXIK catalogue
  // (so Uzbek products auto-fill name + category). Saved per browser; the global
  // databases work without it. Takes effect on the next scan, no reload needed.
  const [relayUrl, setRelayUrl] = useState(getSavedProxyUrl());
  const saveRelay = () => {
    setSavedProxyUrl(relayUrl);
    toast.success(relayUrl.trim() ? t('Katalog manzili saqlandi') : t('Katalog manzili o‘chirildi'));
    refocus();
  };

  // Restock (known product) form.
  const [found, setFound] = useState(null);     // ProductResponse
  const [restockQty, setRestockQty] = useState('1');

  // New-product form.
  const [name, setName] = useState('');
  const [purchasePrice, setPurchasePrice] = useState('');
  const [salePrice, setSalePrice] = useState('');
  const [rate, setRate] = useState(null);
  const [priceCurrency, setPriceCurrency] = useStickyState('barakat.cur.product', 'USD');
  const [quantity, setQuantity] = useState('1');
  const [categoryId, setCategoryId] = useState('');
  const [suggestedCategory, setSuggestedCategory] = useState('');
  const [mxikCode, setMxikCode] = useState('');
  const [fromCatalog, setFromCatalog] = useState(false);

  const usdRate = rate?.available && Number(rate.rate) > 0 ? Number(rate.rate) : null;
  const curr = usdRate ? priceCurrency : 'USD';
  const uzsToUsd = (n) => Math.round((n / usdRate) * 100) / 100;

  useEffect(() => {
    let alive = true;
    ExchangeRateApi.get()
      .then((snapshot) => { if (alive) setRate(snapshot); })
      .catch(() => { if (alive) setRate(null); });
    return () => { alive = false; };
  }, []);

  const switchCurrency = (next) => {
    if (next === curr) return;
    if (!usdRate) {
      toast.error(t("Dollar kursi yuklanmadi — narxni hozircha USDda kiriting"));
      return;
    }
    const conv = (v) => {
      const n = Number(v);
      if (v === '' || v == null || !Number.isFinite(n)) return '';
      return next === 'UZS' ? String(Math.round(n * usdRate)) : String(uzsToUsd(n));
    };
    setPurchasePrice(conv);
    setSalePrice(conv);
    setPriceCurrency(next);
  };

  const priceEquivalent = (v) => {
    const n = Number(v);
    if (!usdRate || !Number.isFinite(n) || n <= 0) return null;
    return curr === 'UZS'
      ? `≈ ${usd(uzsToUsd(n))}`
      : `≈ ${money(Math.round(n * usdRate))} so'm`;
  };

  const priceToUsd = (v) => {
    const n = Number(v) || 0;
    return curr === 'UZS' && usdRate ? uzsToUsd(n) : n;
  };

  const refocus = () => setTimeout(() => inputRef.current?.focus(), 30);

  const backToScan = () => {
    setMode('scan');
    setFound(null);
    refocus();
  };

  const handleScan = async (raw) => {
    const scanned = (raw || '').trim();
    setCode('');
    if (!scanned || busy) {
      return;
    }
    setBusy(true);
    try {
      const result = await ProductApi.scan({ barcode: scanned });
      const canonical = result.barcode || normalizeBarcode(scanned);
      setBarcode(canonical);
      if (result.found) {
        setFound(result.product);
        setRestockQty('1');
        setMode('restock');
      } else {
        // Unknown to the shop. Look the GTIN up in the national catalogue first
        // (browser-side — the hosted backend can't reach Uzbek gov endpoints);
        // if it has nothing, fall back to the global databases via the backend.
        setSearching(true);
        const suggestion = await lookupCatalog(canonical);
        if (suggestion) {
          // National-catalogue hit — existing flow, left exactly as it was.
          setSearching(false);
          prefillNew(suggestion);
        } else if (/^\d+$/.test(canonical)) {
          // Only numeric GTIN/EAN/UPC codes exist in the global databases.
          const global = await lookupGlobal(canonical);
          setSearching(false);
          if (global.timedOut) {
            toast.warn(t("Global baza javob bermadi - qo'lda kiriting"));
            prefillNew(null);
          } else if (global.found) {
            prefillNewFromGlobal(global);
          } else {
            prefillNew(null);
          }
        } else {
          // Non-numeric code (e.g. IMEI) — no global source to try.
          setSearching(false);
          prefillNew(null);
        }
        setMode('new');
      }
    } catch (err) {
      setSearching(false);
      toast.error(err.message);
      refocus();
    }
    setBusy(false);
  };

  // Seed the new-product form from a catalogue hit (or blanks when none).
  const prefillNew = (suggestion) => {
    const s = suggestion || null;
    setName(s?.name || '');
    setMxikCode(s?.mxikCode || '');
    setPurchasePrice('');
    setSalePrice('');
    setQuantity('1');
    setFromCatalog(Boolean(s));
    const suggested = (s?.categoryName || '').trim();
    setSuggestedCategory(suggested);
    const match = suggested
      ? categories.find((c) => c.name.toLowerCase() === suggested.toLowerCase())
      : null;
    // Pre-select an existing matching category; otherwise offer to create it.
    setCategoryId(match ? String(match.id) : suggested ? NEW_CATEGORY : '');
  };

  // Global-database fallback via the backend, capped on the client so a slow
  // external API can never hang the scan. The backend fans the free databases
  // out in parallel then tries the paid/rate-limited ones, so it usually answers
  // in ~1-2 round-trips; 8s leaves room for the hosted VPS's slower hop abroad.
  // Resolves to { found, name?, suggestedCategory? } or { timedOut: true }.
  const lookupGlobal = async (canonical) => {
    const timeout = new Promise((resolve) =>
      setTimeout(() => resolve({ timedOut: true }), 8000));
    try {
      const res = await Promise.race([ProductApi.barcodeLookup(canonical), timeout]);
      if (res.timedOut) {
        return { timedOut: true };
      }
      return res.found
        ? { found: true, name: res.name, suggestedCategory: res.suggestedCategory }
        : { found: false };
    } catch {
      // Backend / network error -> behave like a miss and let the cashier type it.
      return { found: false };
    }
  };

  // Seed the new-product form from a global database hit. These carry no MXIK
  // code, but the backend maps common global categories to local Uzbek buckets;
  // new categories are offered for auto-creation on save.
  const prefillNewFromGlobal = (hit) => {
    setName(hit.name || '');
    setMxikCode('');
    setPurchasePrice('');
    setSalePrice('');
    setQuantity('1');
    setFromCatalog(true);
    const suggested = (hit.suggestedCategory || '').trim();
    setSuggestedCategory(suggested);
    const match = suggested
      ? categories.find((c) => c.name.toLowerCase() === suggested.toLowerCase())
      : null;
    setCategoryId(match ? String(match.id) : suggested ? NEW_CATEGORY : '');
  };

  const submitRestock = async () => {
    if (busy) return;   // guard against a double Enter / scanner trailing CR
    const qty = parseInt(restockQty, 10);
    if (!qty || qty <= 0) {
      toast.error(t('Sonini kiriting'));
      return;
    }
    setBusy(true);
    try {
      const updated = await ProductApi.adjust(found.id, {
        delta: qty,
        reason: 'DELIVERY',
        note: t('Skaner orqali kirim'),
      });
      setLog((entries) => [
        {
          id: Date.now(),
          kind: 'ok',
          text: `${updated.name} +${qty} (${t('jami')} ${updated.quantity} ${t('dona')})`,
        },
        ...entries,
      ]);
      onChanged();
      backToScan();
    } catch (err) {
      toast.error(err.message);
    }
    setBusy(false);
  };

  const createNew = async () => {
    if (busy) return;   // guard against a double-submit
    if (!name.trim()) {
      toast.error(t('Mahsulot nomini kiriting'));
      return;
    }
    setBusy(true);
    try {
      const useSuggested = categoryId === NEW_CATEGORY && suggestedCategory;
      const realCategory = categoryId && categoryId !== NEW_CATEGORY;
      const created = await ProductApi.create({
        name: name.trim(),
        barcode,
        purchasePrice: priceToUsd(purchasePrice),
        salePrice: priceToUsd(salePrice),
        quantity: parseInt(quantity, 10) || 0,
        categoryId: realCategory ? Number(categoryId) : null,
        categoryName: useSuggested ? suggestedCategory : null,
        mxikCode: mxikCode || null,
      });
      setLog((entries) => [
        {
          id: Date.now(),
          kind: 'new',
          text: `${created.name} ${t("omborga qo'shildi")} (${created.quantity} ${t('dona')})`,
        },
        ...entries,
      ]);
      onChanged();
      backToScan();
    } catch (err) {
      toast.error(err.message);
    }
    setBusy(false);
  };

  const suggestedIsNew = suggestedCategory
    && !categories.some((c) => c.name.toLowerCase() === suggestedCategory.toLowerCase());

  return (
    <Modal
      title={t('Shtrix kod skaneri')}
      wide
      onClose={onClose}
      footer={<button className="btn btn-ghost" onClick={onClose}>{t('Yopish')}</button>}
    >
      {mode === 'restock' ? (
        <div>
          <div className="badge badge-naqd" style={{ marginBottom: 12 }}>
            {t('Omborda bor')} — {found?.name}
          </div>
          <p className="muted" style={{ marginBottom: 10 }}>
            {t('Hozirgi qoldiq:')} <b>{found?.quantity} {t('dona')}</b>
            {found?.categoryName ? ` · ${found.categoryName}` : ''}
          </p>
          <div className="field">
            <label>{t('Nechta keldi? (Soni)')}</label>
            <input className="input" type="number" min="1" autoFocus value={restockQty}
                   disabled={busy}
                   onChange={(e) => setRestockQty(e.target.value)}
                   onKeyDown={(e) => { if (e.key === 'Enter' && !busy) { e.preventDefault(); submitRestock(); } }} />
          </div>
          <div className="flex gap-8" style={{ justifyContent: 'flex-end' }}>
            <button className="btn btn-ghost" disabled={busy} onClick={backToScan}>
              {t('Bekor')}
            </button>
            <button className="btn btn-primary" onClick={submitRestock} disabled={busy}>
              {busy ? t('Saqlanmoqda...') : t('Kirim qilish')}
            </button>
          </div>
        </div>
      ) : mode === 'new' ? (
        <div>
          <div className="badge badge-karta" style={{ marginBottom: 12 }}>
            {t('Yangi shtrix kod:')} {barcode}
          </div>
          {fromCatalog ? (
            <p className="amount-pos" style={{ marginBottom: 10, fontSize: 13 }}>
              ✓ {t("Katalog/global bazadan topildi - nomi va toifasi to'ldirildi. Soni va kelish narxini kiriting.")}
            </p>
          ) : (
            <p className="muted" style={{ marginBottom: 10 }}>
              {t("Bu kod katalogda topilmadi. Mahsulot ma'lumotlarini kiriting:")}
            </p>
          )}
          <div className="field">
            <label>{t('Mahsulot nomi *')}</label>
            <input className="input" autoFocus={!fromCatalog} value={name}
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
              <label>{curr === 'UZS' ? t("Kelish narxi (so'm)") : t('Kelish narxi (USD)')}</label>
              <input className="input" type="number" value={purchasePrice}
                     onChange={(e) => setPurchasePrice(e.target.value)} placeholder="0" />
              {priceEquivalent(purchasePrice) && (
                <div className="field-hint" style={{ fontWeight: 600 }}>
                  {priceEquivalent(purchasePrice)}
                </div>
              )}
            </div>
            <div className="field">
              <label>{curr === 'UZS' ? t("Sotilish narxi (so'm, ixtiyoriy)") : t('Sotilish narxi (USD, ixtiyoriy)')}</label>
              <input className="input" type="number" value={salePrice}
                     onChange={(e) => setSalePrice(e.target.value)} placeholder="0" />
              {priceEquivalent(salePrice) && (
                <div className="field-hint" style={{ fontWeight: 600 }}>
                  {priceEquivalent(salePrice)}
                </div>
              )}
            </div>
          </div>
          <div className="form-row">
            <div className="field">
              <label>{t('Soni (dona)')}</label>
              <input className="input" type="number" autoFocus={fromCatalog} value={quantity}
                     onChange={(e) => setQuantity(e.target.value)} />
            </div>
            <div className="field">
              <label>{t('Toifa')}</label>
              <select className="select" value={categoryId}
                      onChange={(e) => setCategoryId(e.target.value)}>
                <option value="">{t('Tanlanmagan')}</option>
                {suggestedIsNew && (
                  <option value={NEW_CATEGORY}>{suggestedCategory} ({t('yangi')})</option>
                )}
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
              {suggestedIsNew && (
                <div className="field-hint">
                  {t('Topilgan toifa saqlashda avtomatik yaratiladi:')} {suggestedCategory}
                </div>
              )}
            </div>
          </div>
          {mxikCode && (
            <div className="field-hint" style={{ marginBottom: 8 }}>
              {t('MXIK kod:')} {mxikCode}
            </div>
          )}
          <div className="flex gap-8" style={{ justifyContent: 'flex-end' }}>
            <button className="btn btn-ghost" disabled={busy} onClick={backToScan}>
              {t('Bekor')}
            </button>
            <button className="btn btn-primary" onClick={createNew} disabled={busy}>
              {busy ? t('Saqlanmoqda...') : t("Omborga qo'shish")}
            </button>
          </div>
        </div>
      ) : (
        <div className="field">
          <label>{t('Shtrix kod')}</label>
          <input
            ref={inputRef}
            className="input"
            autoFocus
            value={code}
            onChange={(e) => setCode(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                handleScan(e.target.value);
              }
            }}
            placeholder={t('Skanerlang yoki kodni kiriting + Enter')}
            style={{ fontSize: 18, fontFamily: 'monospace', letterSpacing: '.04em' }}
            disabled={busy}
          />
          {searching ? (
            <div className="field-hint">{t('Katalog/global bazadan qidirilmoqda...')}</div>
          ) : (
            <div className="field-hint">
              {t("Skanerni mahsulot shtrix kodiga tuting - kod o'zi kiritiladi. Mavjud mahsulot: soni so'raladi. Yangi kod: nomi va toifasi katalog/global bazadan to'ldiriladi.")}
            </div>
          )}
          <details style={{ marginTop: 12 }}>
            <summary className="muted" style={{ cursor: 'pointer', fontSize: 12 }}>
              ⚙ {t('Milliy katalog ulanishi (MXIK) — ixtiyoriy')}
            </summary>
            <div style={{ marginTop: 8 }}>
              <input
                className="input"
                value={relayUrl}
                onChange={(e) => setRelayUrl(e.target.value)}
                placeholder="https://domeningiz.uz/mxik.php"
                style={{ fontSize: 13 }}
              />
              <div className="field-hint">
                {t("O'zbekiston tovarlari nomi va toifasini avtomatik to'ldirish uchun O'zbekistondagi serverga joylangan relay manzili (ops/mxik-proxy.php). Bo'sh qoldirilsa faqat global bazalar ishlaydi. Saqlangach keyingi skanerdan kuchga kiradi.")}
              </div>
              <button className="btn btn-ghost" style={{ marginTop: 6 }} onClick={saveRelay}>
                {t('Saqlash')}
              </button>
            </div>
          </details>
        </div>
      )}

      <div className="card" style={{ marginTop: 14 }}>
        <div className="card-head">
          <h2>{t('Skanerlangan')}</h2>
          <span className="hint">{log.length} {t('ta')}</span>
        </div>
        <div className="card-pad">
          {log.length === 0 ? (
            <div className="faint" style={{ fontSize: 13 }}>{t('Hali skanerlanmadi.')}</div>
          ) : (
            <div className="list-stack">
              {log.map((entry) => (
                <div key={entry.id} style={{ fontSize: 13 }}>
                  <b className={entry.kind === 'new' ? 'amount-pos' : ''}>
                    {entry.kind === 'new' ? '🆕 ' : '✓ '}
                  </b>
                  {entry.text}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}
