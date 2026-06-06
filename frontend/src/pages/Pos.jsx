import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AiApi, CustomerApi, PosApi, ProductApi } from '../api/endpoints.js';
import { useToast } from '../components/Toast.jsx';
import { EmptyState, Loader, PageHeader, Spinner } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { useKeyboard } from '../hooks/useKeyboard.js';
import { useOnline } from '../hooks/useOnline.js';
import { useVoiceInput } from '../hooks/useVoiceInput.js';
import {
  cacheProducts, enqueueCheckout, failedCount, flushQueue, pendingCount,
} from '../lib/offlineDb.js';
import { normalizeBarcode } from '../lib/barcode.js';
import { money } from '../lib/format.js';

const PAYMENT_METHODS = [
  { value: 'NAQD',  label: 'Naqd' },
  { value: 'KARTA', label: 'Karta' },
  { value: 'KASSA', label: 'Kassa' },
  { value: 'QARZGA', label: 'Qarzga' },
];

/**
 * Point-of-sale (POS) screen.
 *
 * Two-column layout:
 *   • LEFT  — product search + barcode scan + click-to-add product grid
 *   • RIGHT — running cart (line discounts), sale-wide discount, method,
 *             customer link, total, "Sotish" submit
 *
 * Shortcuts: Enter in barcode field = add by SKU, F4 = checkout submit,
 * Esc clears the cart.
 */
export function Pos() {
  const t = useT();
  const toast = useToast();
  const navigate = useNavigate();
  const { data: products, loading } = useApi(() => ProductApi.list(), []);
  const { data: customers } = useApi(() => CustomerApi.list().catch(() => []), []);

  const [search, setSearch] = useState('');
  const [cart, setCart] = useState([]); // {id, name, sku, unitPrice, qty, lineDiscount}
  const [discountPercent, setDiscountPercent] = useState('');
  const [discountAmount, setDiscountAmount] = useState('');
  const [method, setMethod] = useState('NAQD');
  const [customerId, setCustomerId] = useState('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [lastSale, setLastSale] = useState(null);
  const [pendingOffline, setPendingOffline] = useState(0);
  const [failedOffline, setFailedOffline] = useState(0);
  const searchRef = useRef(null);
  const online = useOnline();
  const voice = useVoiceInput({ lang: 'ru-RU' });

  // Cache catalog snapshot for offline mode + refresh pending-queue count.
  useEffect(() => {
    if (products && Array.isArray(products)) {
      void cacheProducts(products);
    }
  }, [products]);
  useEffect(() => {
    void pendingCount().then(setPendingOffline);
    void failedCount().then(setFailedOffline);
  }, [lastSale]);

  // Auto-flush queue when connectivity returns.
  useEffect(() => {
    if (online && pendingOffline > 0) {
      flushQueue(async (payload) => {
        await PosApi.checkout(payload);
      }).then((n) => {
        if (n > 0) {
          toast.success(`${n} ${t('ta offline savdo sinxronlandi')}`);
        }
        pendingCount().then(setPendingOffline);
        failedCount().then(setFailedOffline);
      });
    }
  }, [online, pendingOffline, toast, t]);

  // Voice → cart: when the transcript stabilises, push it through the
  // search input so Enter on the wedge / human can finalise the add.
  useEffect(() => {
    if (!voice.listening && voice.transcript) {
      setSearch(voice.transcript);
    }
  }, [voice.listening, voice.transcript]);

  // F4 = checkout, F8 = focus search, Esc = clear cart (when not typing).
  useKeyboard(useMemo(() => ({
    F4: () => doCheckout(),
    F8: () => searchRef.current?.focus(),
    Escape: () => setCart([]),
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }), [cart, method, discountPercent, discountAmount, customerId, note]));

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!products) return [];
    if (!q) return products.slice(0, 24);
    return products.filter((p) =>
      p.name.toLowerCase().includes(q)
      || (p.barcode || '').toLowerCase().includes(q),
    ).slice(0, 50);
  }, [products, search]);

  const addToCart = (p) => {
    if (p.quantity <= 0) {
      toast.error(`${p.name}: ${t('Tugagan')}`);
      return;
    }
    setCart((prev) => {
      const existing = prev.find((it) => it.id === p.id);
      if (existing) {
        if (existing.qty >= p.quantity) {
          toast.error(`${p.name}: ${t('ombor qoldig\'i yetarli emas')}`);
          return prev;
        }
        return prev.map((it) =>
          it.id === p.id ? { ...it, qty: it.qty + 1 } : it);
      }
      return [...prev, {
        id: p.id, name: p.name, sku: p.barcode,
        unitPrice: Number(p.salePrice ?? p.sellingPriceUzs ?? 0),
        stock: p.quantity, qty: 1, lineDiscount: 0,
      }];
    });
  };

  const handleBarcodeEnter = (e) => {
    if (e.key !== 'Enter') return;
    e.preventDefault();
    const q = search.trim();
    if (!q) return;
    // Exact barcode match wins over text search. Normalise both sides so a GS1
    // DataMatrix marking code (with its per-unit serial) still matches the
    // product stored under its plain GTIN.
    const norm = normalizeBarcode(q);
    const exact = products?.find(
      (p) => p.barcode && normalizeBarcode(p.barcode) === norm,
    );
    if (exact) {
      addToCart(exact);
      setSearch('');
      return;
    }
    if (filtered.length === 1) {
      addToCart(filtered[0]);
      setSearch('');
    }
  };

  const updateLine = (id, patch) =>
    setCart((prev) => prev.map((it) => (it.id === id ? { ...it, ...patch } : it)));
  const removeLine = (id) =>
    setCart((prev) => prev.filter((it) => it.id !== id));

  const subtotal = cart.reduce((s, it) => s + it.unitPrice * it.qty, 0);
  const lineDiscountTotal = cart.reduce((s, it) => s + (Number(it.lineDiscount) || 0), 0);
  const afterLine = subtotal - lineDiscountTotal;
  const afterFlat = Math.max(0, afterLine - (Number(discountAmount) || 0));
  const percent = Math.max(0, Math.min(100, Number(discountPercent) || 0));
  const total = Math.round(afterFlat * (1 - percent / 100));

  const doCheckout = async () => {
    if (cart.length === 0) {
      toast.error(t("Savatcha bo'sh"));
      return;
    }
    const payload = {
      items: cart.map((it) => ({
        productId: it.id,
        quantity: it.qty,
        lineDiscountUzs: Number(it.lineDiscount) || 0,
      })),
      discountPercent: percent,
      discountAmount: Number(discountAmount) || 0,
      paymentMethod: method,
      customerId: customerId ? Number(customerId) : null,
      note: note || null,
    };
    setBusy(true);
    try {
      const sale = await PosApi.checkout(payload);
      toast.success(t('Sotuv yakunlandi') + ` (#${sale.id})`);
      setLastSale(sale);
      setCart([]);
      setDiscountAmount('');
      setDiscountPercent('');
      setNote('');
      // keep payment method and customer for the next sale
    } catch (err) {
      // Offline fallback — enqueue, show pending count, recover later.
      // err.status === 0 is the api client's "couldn't reach server" signal
      // (the message is localized, so don't rely on matching its text).
      const isNetwork = !online
        || err.status === 0
        || /Failed to fetch|NetworkError|ECONN|timeout|ulanib bo'lmadi/i.test(err.message || '');
      if (isNetwork) {
        await enqueueCheckout(payload);
        const n = await pendingCount();
        setPendingOffline(n);
        toast.success(t('Offline saqlandi') + ` (${n} ${t('navbatda')})`);
        setCart([]);
      } else {
        toast.error(err.message || t("Sotib bo'lmadi"));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader title={t('Kassa (POS)')} desc={t('Yangi sotuv — savatcha, chegirma, to\'lov')}>
        {!online && <span className="badge badge-qarzga">⚠ {t('Offline')}</span>}
        {failedOffline > 0 && (
          <span className="badge badge-qarzga" title={t('Bu savdolar yuborilmadi — tekshiring')}>
            ❌ {failedOffline} {t('xato')}
          </span>
        )}
        {pendingOffline > 0 && (
          <span className="badge badge-aralash">📤 {pendingOffline} {t('navbatda')}</span>
        )}
        {voice.supported && (
          <button
            className={`btn ${voice.listening ? 'btn-accent' : 'btn-ghost'}`}
            onClick={() => voice.listening ? voice.stop() : voice.start()}
            title={t('Mikrofon orqali qidiruv')}
          >
            🎤 {voice.listening ? t('Tinglayman...') : t('Mikrofon')}
          </button>
        )}
        <Link to="/pos/history" className="btn btn-ghost">📋 {t('Sotuvlar tarixi')}</Link>
      </PageHeader>

      <div className="pos-grid">
        {/* ============ LEFT: product picker ============ */}
        <div className="card pos-products">
          <div className="card-pad" style={{ paddingBottom: 8 }}>
            <input
              ref={searchRef}
              className="input"
              placeholder={t('Shtrix-kod yoki nom...')}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onKeyDown={handleBarcodeEnter}
              autoFocus
            />
            <div className="faint" style={{ fontSize: 11, marginTop: 4 }}>
              Enter = {t("savatchaga qo'shish")} · F8 = {t('qidiruv')} · F4 = {t('Sotish')}
            </div>
          </div>
          <Loader loading={loading}>
            <div className="pos-product-grid">
              {filtered.length === 0 && (
                <EmptyState icon="🔎" text={t('Mahsulot topilmadi')} />
              )}
              {filtered.map((p) => (
                <button
                  key={p.id}
                  className={`pos-product ${p.quantity <= 0 ? 'out' : ''}`}
                  onClick={() => addToCart(p)}
                  disabled={p.quantity <= 0}
                  title={`${p.name} (qoldiq: ${p.quantity})`}
                >
                  <div className="pp-name">{p.name}</div>
                  <div className="pp-price">{money(p.salePrice ?? p.sellingPriceUzs)} so'm</div>
                  <div className="pp-stock faint">
                    {t('qoldiq')}: <strong>{p.quantity}</strong>
                  </div>
                </button>
              ))}
            </div>
          </Loader>
        </div>

        {/* ============ RIGHT: cart + checkout ============ */}
        <div className="card pos-cart">
          <div className="card-head">
            <h2>{t('Savatcha')} ({cart.length})</h2>
            {cart.length > 0 && (
              <button className="btn btn-ghost btn-sm" onClick={() => setCart([])}>
                {t('Tozalash')}
              </button>
            )}
          </div>
          <div className="pos-cart-items">
            {cart.length === 0 ? (
              <EmptyState icon="🛒" text={t('Mahsulot qo\'shing')} />
            ) : cart.map((it) => (
              <div key={it.id} className="pos-cart-line">
                <div className="pcl-name">
                  <strong>{it.name}</strong>
                  <div className="faint" style={{ fontSize: 11 }}>{it.sku || ''}</div>
                </div>
                <input
                  type="number"
                  className="input"
                  min="1"
                  max={it.stock}
                  value={it.qty}
                  onChange={(e) => updateLine(it.id, { qty: Math.max(1, Math.min(it.stock, Number(e.target.value) || 1)) })}
                  style={{ width: 60 }}
                />
                <span className="mono faint">×</span>
                <span className="mono">{money(it.unitPrice)}</span>
                <input
                  type="number"
                  className="input"
                  min="0"
                  placeholder="-0"
                  value={it.lineDiscount || ''}
                  onChange={(e) => updateLine(it.id, { lineDiscount: Number(e.target.value) || 0 })}
                  title={t('Chiziq chegirmasi (UZS)')}
                  style={{ width: 80 }}
                />
                <span className="mono pcl-line">
                  = {money(it.unitPrice * it.qty - (Number(it.lineDiscount) || 0))}
                </span>
                <button className="icon-btn danger" onClick={() => removeLine(it.id)}>×</button>
              </div>
            ))}
          </div>

          <div className="pos-discount">
            <div className="field" style={{ margin: 0 }}>
              <label>{t('Chegirma %')}</label>
              <input
                className="input" type="number" min="0" max="100"
                value={discountPercent}
                onChange={(e) => setDiscountPercent(e.target.value)}
              />
            </div>
            <div className="field" style={{ margin: 0 }}>
              <label>{t('Chegirma summa (UZS)')}</label>
              <input
                className="input" type="number" min="0"
                value={discountAmount}
                onChange={(e) => setDiscountAmount(e.target.value)}
              />
            </div>
          </div>

          <div className="pos-checkout">
            <div className="field" style={{ margin: 0 }}>
              <label>{t('To\'lov turi')}</label>
              <select className="input" value={method} onChange={(e) => setMethod(e.target.value)}>
                {PAYMENT_METHODS.map((m) => (
                  <option key={m.value} value={m.value}>{t(m.label)}</option>
                ))}
              </select>
            </div>
            <div className="field" style={{ margin: 0 }}>
              <label>{t('Mijoz')} ({t('ixtiyoriy')})</label>
              <select className="input" value={customerId} onChange={(e) => setCustomerId(e.target.value)}>
                <option value="">—</option>
                {(customers || []).map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="pos-totals">
            <Row label={t('Subtotal')} value={money(subtotal)} />
            {lineDiscountTotal > 0 && (
              <Row label={t('Chiziq chegirmalari')} value={`- ${money(lineDiscountTotal)}`} muted />
            )}
            {(Number(discountAmount) > 0 || percent > 0) && (
              <Row label={t('Chegirma')} value={`- ${money(afterLine - total)}`} muted />
            )}
            <Row label={t('JAMI')} value={`${money(total)} so'm`} big />
          </div>

          <button
            className="btn btn-primary"
            style={{ width: '100%', padding: 14, fontSize: 16, marginTop: 12 }}
            onClick={doCheckout}
            disabled={busy || cart.length === 0}
          >
            {busy ? <Spinner /> : `💰 ${t('Sotish')} (F4)`}
          </button>

          {lastSale && (
            <div className="pos-last-sale">
              ✓ {t('Oxirgi sotuv')}: #{lastSale.id} · {money(lastSale.totalUzs)} so'm
              <button className="btn btn-ghost btn-sm" onClick={() => navigate(`/pos/history`)} style={{ marginLeft: 8 }}>
                {t('Ko\'rish')}
              </button>
            </div>
          )}
        </div>
      </div>
    </>
  );
}

function Row({ label, value, big, muted }) {
  return (
    <div className="pos-total-row" style={{
      fontSize: big ? 18 : 13,
      fontWeight: big ? 700 : 500,
      color: muted ? '#9ca3af' : '#111827',
      marginTop: big ? 8 : 0,
      borderTop: big ? '1px solid #e5e7eb' : 'none',
      paddingTop: big ? 8 : 0,
    }}>
      <span>{label}</span>
      <span className="mono">{value}</span>
    </div>
  );
}
