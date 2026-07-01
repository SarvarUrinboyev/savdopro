import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CustomerApi, PosApi, ProductApi } from '../api/endpoints.js';
import { ImeiCaptureModal } from '../components/ImeiCaptureModal.jsx';
import { ConfirmDialog, Modal } from '../components/Modal.jsx';
import { useToast } from '../components/Toast.jsx';
import { Spinner } from '../components/ui.jsx';
import { useAuth } from '../context/Auth.jsx';
import { useT } from '../context/Settings.jsx';
import { useShop } from '../context/Shop.jsx';
import { useApi } from '../hooks/useApi.js';
import { useOnline } from '../hooks/useOnline.js';
import { useVoiceInput } from '../hooks/useVoiceInput.js';
import {
  cacheCustomers, cacheProducts, cancelQueued, clearOldSynced, enqueueCheckout, failedCount, flushQueue, getCachedCustomers,
  getCachedProducts, listQueue, pendingCount, pendingProductQuantities, retryQueued,
} from '../lib/offlineDb.js';
import { normalizeBarcode } from '../lib/barcode.js';
import { usd } from '../lib/format.js';

const PAYMENT_METHODS = [
  { value: 'NAQD',  label: 'Naqd' },
  { value: 'KARTA', label: 'Karta' },
  { value: 'KASSA', label: 'Kassa' },
  { value: 'QARZGA', label: 'Qarzga' },
];

const TOUCH_KEY_ROWS = [
  ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'],
  ['Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'],
  ['A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'],
  ['Z', 'X', 'C', 'V', 'B', 'N', 'M', "'", '-'],
];

/** Small stroke icons for the side-panel action keys. */
const AIco = {
  x: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4"
         strokeLinecap="round"><path d="M6 6l12 12M18 6L6 18" /></svg>
  ),
  receipt: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M6 3h12v18l-2-1.4L14 21l-2-1.4L10 21l-2-1.4L6 21V3z" />
      <path d="M9 8h6M9 12h6" />
    </svg>
  ),
  person: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
         strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="8" r="3.4" />
      <path d="M5 20c.8-3.4 3.6-5 7-5s6.2 1.6 7 5" />
    </svg>
  ),
  lock: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
         strokeLinecap="round" strokeLinejoin="round">
      <rect x="5" y="11" width="14" height="9" rx="2" />
      <path d="M8 11V8a4 4 0 018 0v3" />
    </svg>
  ),
  sync: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 11a8 8 0 00-14.9-3M4 13a8 8 0 0014.9 3" />
      <path d="M5 4v4h4M19 20v-4h-4" />
    </svg>
  ),
  burger: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2"
         strokeLinecap="round">
      <line x1="4" y1="7" x2="20" y2="7" />
      <line x1="4" y1="12" x2="20" y2="12" />
      <line x1="4" y1="17" x2="20" y2="17" />
    </svg>
  ),
  home: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 11.5L12 4l9 7.5" />
      <path d="M5.5 10.5V20h13v-9.5" />
      <path d="M9.5 20v-5h5v5" />
    </svg>
  ),
};

let CHECK_SEQ = 1;
// Discounts live on the check (not the screen) so each open receipt keeps
// its own % / flat amount when the cashier flips between Chek № tabs.
const newCheckObj = () => ({
  id: CHECK_SEQ++, cart: [], customerId: '', note: '',
  discountPercent: '', discountAmount: '',
});

/**
 * Point-of-sale (POS) screen, styled as a classic cash-register window
 * (YesPOS-like):
 *
 *   • TOP    — the open receipt as a table (№, Nomi/SKU, Narxi, Miqdor,
 *              Qoldiq, Chegirma, Summa) with a brand watermark when empty
 *   • MIDDLE — receipt tabs (Tanlanganlar / Chek № n / Yangi chek) + a
 *              search strip + the product catalogue table (SKU, Nomi,
 *              Narxi, Qoldiq)
 *   • BOTTOM — status bar (ИД, Hodim, Filial, POS, Sinxronizatsiya)
 *   • RIGHT  — action keys (O'chirish, Cheklar, Klient, Bloklash), the
 *              numpad with Miqdor/ABC/Qidirish modes and the To'lov key
 *
 * Shortcuts: Enter in the search strip = add by barcode/SKU, F4 = payment,
 * F8 = focus search, Esc clears the row selection.
 */
export function Pos() {
  const t = useT();
  const toast = useToast();
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const { activeShop } = useShop();
  const [catalogSource, setCatalogSource] = useState('online');
  const loadProducts = useCallback(async () => {
    try {
      const list = await ProductApi.list();
      await cacheProducts(list);
      setCatalogSource('online');
      return list;
    } catch (err) {
      const cached = await getCachedProducts();
      if (cached.length > 0) {
        setCatalogSource('offline');
        return cached;
      }
      throw err;
    }
  }, []);
  const { data: products, loading, error: productError, reload: reloadProducts } =
    useApi(loadProducts, [loadProducts]);
  // Customers mirror the product snapshot: cache online, fall back offline —
  // a QARZGA sale can then still pick its customer and queue the checkout.
  const loadCustomers = useCallback(async () => {
    try {
      const list = await CustomerApi.list();
      await cacheCustomers(list);
      return list;
    } catch (err) {
      const cached = await getCachedCustomers();
      return cached.length > 0 ? cached : [];
    }
  }, []);
  const { data: customers } = useApi(loadCustomers, [loadCustomers]);

  // ----- receipts (multi-check, like the register's Chek № tabs) -----
  const [checks, setChecks] = useState(() => [newCheckObj()]);
  const [activeId, setActiveId] = useState(() => checks[0].id);
  const activeCheck = checks.find((c) => c.id === activeId) || checks[0];
  const cart = activeCheck.cart;
  const setCart = (updater) => setChecks((prev) => prev.map((c) =>
    c.id === activeId
      ? { ...c, cart: typeof updater === 'function' ? updater(c.cart) : updater }
      : c));
  const patchCheck = (patch) => setChecks((prev) =>
    prev.map((c) => (c.id === activeId ? { ...c, ...patch } : c)));

  const [selectedLine, setSelectedLine] = useState(null);
  const [bottomView, setBottomView] = useState('catalog'); // 'catalog' | 'selected'
  const [search, setSearch] = useState('');
  const [padMode, setPadMode] = useState('miqdor'); // 'miqdor' | 'qidirish'
  const [keyboardOpen, setKeyboardOpen] = useState(false);
  const [qtyBuf, setQtyBuf] = useState('');
  const discountPercent = activeCheck.discountPercent ?? '';
  const discountAmount = activeCheck.discountAmount ?? '';
  const [method, setMethod] = useState('NAQD');
  const [payOpen, setPayOpen] = useState(false);
  // Non-null while the IMEI-capture dialog is open (the lines needing IMEI).
  const [imeiLines, setImeiLines] = useState(null);
  const [clientOpen, setClientOpen] = useState(false);
  const [clientQuery, setClientQuery] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  const [confirmClear, setConfirmClear] = useState(false);
  const [confirmLock, setConfirmLock] = useState(false);
  const [busy, setBusy] = useState(false);
  const [lastSale, setLastSale] = useState(null);
  const [pendingOffline, setPendingOffline] = useState(0);
  const [failedOffline, setFailedOffline] = useState(0);
  const [pendingByProduct, setPendingByProduct] = useState({});
  const [syncOpen, setSyncOpen] = useState(false);
  const [queueRows, setQueueRows] = useState([]);
  const [syncBusy, setSyncBusy] = useState(false);
  const [syncAt, setSyncAt] = useState('—');
  const searchRef = useRef(null);
  const online = useOnline();
  const voice = useVoiceInput({ lang: 'ru-RU' });

  const refreshOfflineState = useCallback(async () => {
    const [pending, failed, reserved, rows] = await Promise.all([
      pendingCount(),
      failedCount(),
      pendingProductQuantities(),
      listQueue(),
    ]);
    setPendingOffline(pending);
    setFailedOffline(failed);
    setPendingByProduct(reserved);
    setQueueRows(rows);
  }, []);

  // Catalog snapshot: online list is persisted in IndexedDB, offline list is
  // read from it by loadProducts(). syncAt tells the cashier which one is live.
  useEffect(() => {
    if (products && Array.isArray(products)) {
      setSyncAt(catalogSource === 'offline'
        ? t('Keshdan')
        : new Date().toLocaleTimeString('uz-UZ', { hour12: false }));
    }
  }, [catalogSource, products, t]);
  useEffect(() => {
    void clearOldSynced();
    void refreshOfflineState();
  }, [lastSale, refreshOfflineState]);

  const runQueueFlush = useCallback(async ({ silent = false } = {}) => {
    if (syncBusy) return 0;
    setSyncBusy(true);
    try {
      const n = await flushQueue(async (payload) => {
        await PosApi.checkout(payload);
      });
      if (n > 0) {
        toast.success(`${n} ${t('ta offline savdo sinxronlandi')}`);
        reloadProducts();
      } else if (!silent) {
        toast.info(t('Yuboriladigan offline chek yo\'q'));
      }
      await refreshOfflineState();
      return n;
    } finally {
      setSyncBusy(false);
    }
  }, [reloadProducts, refreshOfflineState, syncBusy, t, toast]);

  // Auto-flush queue when connectivity returns.
  useEffect(() => {
    if (online && pendingOffline > 0) {
      void runQueueFlush({ silent: true });
    }
  }, [online, pendingOffline, runQueueFlush]);

  // Voice → search: when the transcript stabilises, push it through the
  // search strip so Enter on the wedge / human can finalise the add.
  useEffect(() => {
    if (!voice.listening && voice.transcript) {
      setSearch(voice.transcript);
      setBottomView('catalog');
    }
  }, [voice.listening, voice.transcript]);

  const catalogProducts = useMemo(() => (products || []).map((p) => {
    const reserved = pendingByProduct[p.id] || 0;
    const quantity = Math.max(0, Number(p.quantity || 0) - reserved);
    return {
      ...p,
      serverQuantity: p.quantity,
      pendingReserved: reserved,
      quantity,
    };
  }), [pendingByProduct, products]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!catalogProducts) return [];
    if (!q) return catalogProducts.slice(0, 30);
    return catalogProducts.filter((p) =>
      p.name.toLowerCase().includes(q)
      || (p.barcode || '').toLowerCase().includes(q),
    ).slice(0, 50);
  }, [catalogProducts, search]);

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
        requiresImei: Boolean(p.requiresImei),
      }];
    });
    // The register keeps the just-rung row selected so the numpad's
    // Miqdor mode immediately edits its quantity.
    setSelectedLine(p.id);
    setQtyBuf('');
  };

  const submitSearch = () => {
    // A wedge scan must not mutate the check that is being paid right now —
    // the paid cart was snapshotted, so the extra line would be lost.
    if (payOpen || busy) return;
    const q = search.trim();
    if (!q) return;
    // Exact barcode match wins over text search. Normalise both sides so a GS1
    // DataMatrix marking code (with its per-unit serial) still matches the
    // product stored under its plain GTIN.
    const norm = normalizeBarcode(q);
    const exact = catalogProducts.find(
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
  const handleBarcodeEnter = (e) => {
    if (e.key !== 'Enter') return;
    e.preventDefault();
    submitSearch();
  };

  const updateLine = (id, patch) =>
    setCart((prev) => prev.map((it) => (it.id === id ? { ...it, ...patch } : it)));
  const removeLine = (id) => {
    setCart((prev) => prev.filter((it) => it.id !== id));
    if (selectedLine === id) {
      setSelectedLine(null);
      setQtyBuf('');
    }
  };

  const selectLine = (id) => {
    setSelectedLine(id);
    setQtyBuf('');
  };

  // ----- numpad -----
  const padPress = (key) => {
    if (padMode !== 'miqdor') {
      if (key === '.') return;
      setSearch((s) => s + key);
      setBottomView('catalog');
      return;
    }
    const targetId = selectedLine ?? (cart.length ? cart[cart.length - 1].id : null);
    if (targetId == null) {
      toast.error(t("Avval mahsulot qo'shing"));
      return;
    }
    if (selectedLine == null) setSelectedLine(targetId);
    if (key === '.' && qtyBuf.includes('.')) return;
    if (key === '00' && !qtyBuf) return;
    const buf = (qtyBuf + key).replace(/^0+(?=\d)/, '');
    setQtyBuf(buf);
    const v = Number(buf);
    if (Number.isFinite(v) && v > 0) {
      const line = cart.find((it) => it.id === targetId);
      updateLine(targetId, { qty: Math.min(line?.stock ?? Infinity, v) });
    }
  };
  const padClear = () => {
    if (padMode === 'miqdor') setQtyBuf('');
    else setSearch('');
  };
  const focusSearch = () => {
    setPadMode('qidirish');
    setBottomView('catalog');
    searchRef.current?.focus();
  };
  const toggleTouchKeyboard = () => {
    setPadMode('qidirish');
    setBottomView('catalog');
    setKeyboardOpen((open) => {
      const next = !open;
      if (next) {
        setTimeout(() => searchRef.current?.focus(), 0);
      }
      return next;
    });
  };
  const touchKeyPress = (key) => {
    setPadMode('qidirish');
    setBottomView('catalog');
    setSearch((s) => s + key.toLowerCase());
  };
  const touchBackspace = () => setSearch((s) => s.slice(0, -1));

  // ----- action keys -----
  const onDelete = () => {
    if (selectedLine != null && cart.some((it) => it.id === selectedLine)) {
      removeLine(selectedLine);
    } else if (cart.length > 0) {
      setConfirmClear(true);
    }
  };
  const newCheck = () => {
    const c = newCheckObj();
    setChecks((prev) => [...prev, c]);
    setActiveId(c.id);
    setSelectedLine(null);
    setQtyBuf('');
    setBottomView('catalog');
  };
  const switchCheck = (id) => {
    setActiveId(id);
    setSelectedLine(null);
    setQtyBuf('');
  };
  /**
   * Drop the paid (or cleared) receipt. Functional updater on purpose: the
   * call comes after an awaited checkout, and during the in-flight request
   * the cashier may have opened/edited other checks — a snapshot from the
   * click-time closure would silently wipe that work.
   */
  const finishCheck = (paidId) => {
    setChecks((prev) => {
      const rest = prev.filter((c) => c.id !== paidId);
      return rest.length ? rest : [newCheckObj()];
    });
    setSelectedLine(null);
    setQtyBuf('');
    setBottomView('catalog');
  };
  // If the check activeId points at was removed (paid while the cashier sat
  // on it), land on the first open one. Checks the cashier switched to
  // mid-request stay active untouched.
  useEffect(() => {
    if (checks.length > 0 && !checks.some((c) => c.id === activeId)) {
      setActiveId(checks[0].id);
    }
  }, [checks, activeId]);
  const doSync = async () => {
    reloadProducts();
    if (!online) {
      toast.warn(t('Internet yo\'q — navbat saqlanib turibdi'));
      await refreshOfflineState();
      return;
    }
    await runQueueFlush();
  };

  // ----- totals -----
  const subtotal = cart.reduce((s, it) => s + it.unitPrice * it.qty, 0);
  const lineDiscountTotal = cart.reduce((s, it) => s + (Number(it.lineDiscount) || 0), 0);
  const afterLine = subtotal - lineDiscountTotal;
  const afterFlat = Math.max(0, afterLine - (Number(discountAmount) || 0));
  const percent = Math.max(0, Math.min(100, Number(discountPercent) || 0));
  // Round to cents, matching the backend's setScale(2, HALF_UP) — whole-dollar
  // rounding here would display a different total than what is charged.
  const total = Math.round(afterFlat * (1 - percent / 100) * 100) / 100;

  /**
   * Bound the checkout wait: a black-holed request (half-open socket, hung
   * backend) would otherwise leave busy=true forever and brick the till.
   * The timeout deliberately does NOT enqueue an offline retry — the server
   * may have committed the sale, and a replay would charge the customer twice.
   */
  const withTimeout = (promise, ms) => new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      const err = new Error(
        t("Server javob bermadi — sotuv o'tgan-o'tmaganini Cheklar tarixidan tekshiring"),
      );
      err.name = 'CheckoutTimeout';
      reject(err);
    }, ms);
    promise.then(
      (v) => { clearTimeout(timer); resolve(v); },
      (e) => { clearTimeout(timer); reject(e); },
    );
  });

  const doCheckout = async (capturedDevices = null) => {
    if (busy) return;
    if (cart.length === 0) {
      toast.error(t("Savatcha bo'sh"));
      return;
    }
    // IMEI-tracked lines: collect each unit's IMEI/serial before paying. The
    // modal calls doCheckout(captured) back once the cashier confirms.
    if (!capturedDevices) {
      const need = cart.filter((it) => it.requiresImei);
      if (need.length > 0) {
        setImeiLines(need.map((it) => ({ id: it.id, name: it.name, qty: it.qty })));
        return;
      }
    }
    const devicesFor = (id) => (capturedDevices && capturedDevices[id]) || undefined;
    const payload = {
      items: cart.map((it) => ({
        productId: it.id,
        quantity: it.qty,
        lineDiscountUzs: Number(it.lineDiscount) || 0,
        devices: devicesFor(it.id),
      })),
      discountPercent: percent,
      discountAmount: Number(discountAmount) || 0,
      paymentMethod: method,
      customerId: activeCheck.customerId ? Number(activeCheck.customerId) : null,
      note: activeCheck.note || null,
    };
    // Capture which check is being paid BEFORE the await — the cashier may
    // switch tabs while the request is in flight.
    const paidId = activeCheck.id;
    setBusy(true);
    try {
      const sale = await withTimeout(PosApi.checkout(payload), 30000);
      toast.success(t('Sotuv yakunlandi') + ` (#${sale.id})`);
      setLastSale(sale);
      finishCheck(paidId);
      setPayOpen(false);
      reloadProducts(); // refresh Qoldiq numbers after the sale
      // keep payment method for the next sale
    } catch (err) {
      if (err.name === 'CheckoutTimeout') {
        // Don't auto-queue: the sale may have landed server-side; let the
        // cashier verify in history instead of risking a double charge.
        toast.error(err.message);
        return;
      }
      // Offline fallback — enqueue, show pending count, recover later.
      // err.status === 0 is the api client's "couldn't reach server" signal
      // (the message is localized, so don't rely on matching its text).
      const isNetwork = !online
        || err.status === 0
        || /Failed to fetch|NetworkError|ECONN|timeout|ulanib bo'lmadi/i.test(err.message || '');
      if (isNetwork) {
        await enqueueCheckout(payload, {
          total,
          method,
          customerName: activeCheck.customerId
            ? (customers || []).find((c) => String(c.id) === String(activeCheck.customerId))?.name
            : null,
          note: activeCheck.note || null,
          itemCount: cart.length,
          items: cart.map((it) => ({
            productId: it.id,
            name: it.name,
            sku: it.sku,
            quantity: it.qty,
            unitPrice: it.unitPrice,
            lineDiscount: Number(it.lineDiscount) || 0,
            lineTotal: it.unitPrice * it.qty - (Number(it.lineDiscount) || 0),
          })),
        });
        const n = await pendingCount();
        await refreshOfflineState();
        toast.success(t('Offline saqlandi') + ` (${n} ${t('navbatda')})`);
        finishCheck(paidId);
        setPayOpen(false);
      } else {
        toast.error(err.message || t("Sotib bo'lmadi"));
      }
    } finally {
      setBusy(false);
    }
  };

  // F4 = payment, F8 = search — registered raw so they fire even while the
  // cursor sits in the search strip (where a cashier lives all day).
  // Esc clears the row selection when no dialog is open (dialogs own Esc).
  useEffect(() => {
    const handler = (e) => {
      if (e.key === 'F4') {
        e.preventDefault();
        if (payOpen) doCheckout();
        else setPayOpen(true);
      } else if (e.key === 'F8') {
        e.preventDefault();
        if (!payOpen) focusSearch(); // don't pull focus behind the pay dialog
      } else if (e.key === 'Escape') {
        if (menuOpen) setMenuOpen(false);
        else if (!payOpen && !clientOpen && !confirmClear && !confirmLock) {
          setSelectedLine(null);
          setQtyBuf('');
        }
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  });

  const activeIdx = checks.findIndex((c) => c.id === activeId);
  const customerName = activeCheck.customerId
    ? (customers || []).find((c) => String(c.id) === String(activeCheck.customerId))?.name
    : null;
  const brandName = user?.brand?.name || 'SavdoPRO';
  const openSyncCenter = async () => {
    setSyncOpen(true);
    setQueueRows(await listQueue());
  };
  const retryOfflineRow = async (id) => {
    await retryQueued(id);
    await refreshOfflineState();
    if (online) {
      await runQueueFlush();
    } else {
      toast.info(t('Chek qayta navbatga qo\'yildi'));
    }
  };
  const cancelOfflineRow = async (id) => {
    await cancelQueued(id);
    await refreshOfflineState();
    toast.info(t('Offline chek bekor qilindi'));
  };
  const filteredClients = useMemo(() => {
    const q = clientQuery.trim().toLowerCase();
    const list = customers || [];
    if (!q) return list.slice(0, 50);
    return list.filter((c) =>
      (c.name || '').toLowerCase().includes(q)
      || (c.phone || '').toLowerCase().includes(q)).slice(0, 50);
  }, [customers, clientQuery]);

  const catalogRows = bottomView === 'selected'
    ? cart.map((it) => ({
        key: `sel-${it.id}`, sku: it.sku, name: it.name,
        price: it.unitPrice, stock: it.stock, out: false,
        onClick: () => selectLine(it.id),
      }))
    : filtered.map((p) => ({
        key: p.id, sku: p.barcode, name: p.name,
        price: p.salePrice ?? p.sellingPriceUzs, stock: p.quantity,
        reserved: p.pendingReserved || 0,
        out: p.quantity <= 0,
        onClick: () => addToCart(p),
      }));

  return (
    <div className="ypos">
      {/* ============ LEFT: receipt + catalogue ============ */}
      <div className="ypos-main">
        {/* receipt column headers */}
        <div className="ypos-cart-head">
          <div className="ych-cell">№</div>
          <div className="ych-cell ych-nomi">
            <div>{t('Nomi')}</div>
            <div className="sub">{t('SKU / Shtrix Kod')}</div>
          </div>
          <div className="ych-cell num">{t('Narxi')}</div>
          <div className="ych-cell num">{t('Miqdor')}</div>
          <div className="ych-cell num">{t('Qoldiq')}</div>
          <div className="ych-cell num">{t('Chegirma')}</div>
          <div className="ych-cell num last">{t('Summa')}</div>
        </div>

        {/* the open receipt */}
        <div className="ypos-cart-body">
          {cart.length === 0 && (
            <div className="ypos-watermark" aria-hidden="true">
              <span className="wm-a">{brandName.replace(/PRO$/i, '')}</span>
              <span className="wm-b">{/pro$/i.test(brandName) ? 'PRO' : ''}</span>
            </div>
          )}
          {cart.map((it, i) => (
            <div
              key={it.id}
              className={`ypos-cart-row ${selectedLine === it.id ? 'selected' : ''}`}
              onClick={() => selectLine(it.id)}
            >
              <div className="ycr-cell">{i + 1}</div>
              <div className="ycr-cell ycr-nomi">
                <div className="nm">{it.name}</div>
                {it.sku && <div className="sk">{it.sku}</div>}
              </div>
              <div className="ycr-cell num mono">{usd(it.unitPrice)}</div>
              <div className="ycr-cell num">
                <input
                  type="number"
                  className="ypos-cell-input"
                  min="1"
                  max={it.stock}
                  value={it.qty}
                  onChange={(e) => updateLine(it.id, {
                    qty: Math.max(1, Math.min(it.stock, Number(e.target.value) || 1)),
                  })}
                />
              </div>
              <div className="ycr-cell num mono">{it.stock}</div>
              <div className="ycr-cell num">
                <input
                  type="number"
                  className="ypos-cell-input"
                  min="0"
                  placeholder="0"
                  value={it.lineDiscount || ''}
                  onChange={(e) => updateLine(it.id, {
                    lineDiscount: Number(e.target.value) || 0,
                  })}
                  title={t('Chiziq chegirmasi (USD)')}
                />
              </div>
              <div className="ycr-cell num mono last total">
                {usd(it.unitPrice * it.qty - (Number(it.lineDiscount) || 0))}
              </div>
            </div>
          ))}
        </div>

        {/* receipt tabs */}
        <div className="ypos-tabs">
          <button
            type="button"
            className={`ypos-tab ${bottomView === 'selected' ? 'active' : ''}`}
            onClick={() => setBottomView((v) => (v === 'selected' ? 'catalog' : 'selected'))}
          >
            {t('Tanlanganlar')}
          </button>
          {checks.map((c, i) => (
            <button
              type="button"
              key={c.id}
              className={`ypos-tab ${c.id === activeId ? 'active' : ''}`}
              onClick={() => switchCheck(c.id)}
            >
              {c.id === activeId && <span className="tick">✓ </span>}
              {t('Chek')} № {i + 1}
              {c.cart.length > 0 && <span className="cnt"> ({c.cart.length})</span>}
            </button>
          ))}
          <button type="button" className="ypos-tab" onClick={newCheck}>
            {t('Yangi chek')}
          </button>
          {customerName && (
            <span className="ypos-client-chip" title={t('Mijoz')}>
              👤 {customerName}
            </span>
          )}
          <span className="ypos-tabs-total mono">
            {t('Jami')}: <b>{usd(total)}</b>
          </span>
        </div>

        {/* search strip — barcode wedge / name lookup */}
        <div className="ypos-searchrow">
          <input
            ref={searchRef}
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setBottomView('catalog');
            }}
            onFocus={() => setPadMode('qidirish')}
            onKeyDown={handleBarcodeEnter}
            placeholder={`${t('Nomi yoki SKU / shtrix kod')}…  (Enter = ${t("qo'shish")}, F4 = ${t("To'lov")})`}
            autoFocus
          />
        </div>

        {/* catalogue (card grid) / selected-items (table) */}
        <div className={`ypos-catalog${bottomView === 'selected' ? '' : ' ypos-catalog-grid'}`}>
          <div className="ypos-cat-head">
            <div>{t('SKU')}</div>
            <div>{t('Nomi')}</div>
            <div className="num">{t('Narxi')}</div>
            <div className="num">{t('Qoldiq')}</div>
          </div>
          {loading ? (
            <div className="ypos-cat-empty"><Spinner /></div>
          ) : catalogRows.length === 0 ? (
            <div className="ypos-cat-empty">
              {productError
                ? t('Katalog keshda yo\'q — internet ulanganda qayta sinxron qiling')
                : (bottomView === 'selected' ? t('Chek bo\'sh') : t('Mahsulot topilmadi'))}
            </div>
          ) : bottomView === 'selected' ? (
            catalogRows.map((row) => (
              <div
                key={row.key}
                className={`ypos-cat-row ${row.out ? 'out' : ''}`}
                onClick={row.out ? undefined : row.onClick}
                title={row.name}
              >
                <div className="mono">{row.sku || '—'}</div>
                <div className="nm">{row.name}</div>
                <div className="num mono">{usd(row.price)}</div>
                <div className="num mono">
                  {row.stock}
                  {row.reserved > 0 && (
                    <span className="ypos-reserved" title={t('Offline navbatda rezerv')}>
                      -{row.reserved}
                    </span>
                  )}
                </div>
              </div>
            ))
          ) : (
            // Premium product-card grid. Same data + click handler as the list —
            // the whole card is the add-to-cart target, so POS logic is unchanged.
            catalogRows.map((row) => (
              <div
                key={row.key}
                className={`ypos-prod-card ${row.out ? 'out' : ''}`}
                onClick={row.out ? undefined : row.onClick}
                title={row.name}
              >
                {!row.out && (
                  <span className="ypos-prod-add" aria-hidden="true">+</span>
                )}
                <div className="ypos-prod-ico">
                  {(row.name || '?').trim().charAt(0).toUpperCase()}
                </div>
                <div className="ypos-prod-name">{row.name}</div>
                <div className="ypos-prod-foot">
                  <span className="ypos-prod-price">{usd(row.price)}</span>
                  <span className={`ypos-prod-stock ${row.stock <= 5 ? 'low' : ''}`}>
                    {row.stock}{row.reserved > 0 ? `−${row.reserved}` : ''}
                  </span>
                </div>
              </div>
            ))
          )}
        </div>

        {/* status bar */}
        <div className="ypos-statusbar">
          <span className="sb-item">ИД: <b>{user?.accountId ?? user?.id ?? '—'}</b></span>
          <span className="sb-item">{t('Hodim')}: <b>{user?.fullName || user?.username || '—'}</b></span>
          <span className="sb-item">{t('Filial')}: <b>{activeShop?.name || user?.accountName || '—'}</b></span>
          <span className="sb-item">POS: <b>Kassa-1</b></span>
          <span className="sb-item">{t('Sinxronizatsiya')}: <b>{syncAt}</b></span>
          <span className="sb-right">
            {!online && <span className="badge badge-qarzga">⚠ {t('Offline')}</span>}
            {failedOffline > 0 && (
              <button
                type="button"
                className="badge badge-qarzga sb-queue-btn"
                title={t('Bu savdolar yuborilmadi — tekshiring')}
                onClick={openSyncCenter}
              >
                ❌ {failedOffline} {t('xato')}
              </button>
            )}
            {pendingOffline > 0 && (
              <button
                type="button"
                className="badge badge-aralash sb-queue-btn"
                onClick={openSyncCenter}
              >
                📤 {pendingOffline} {t('navbatda')}
              </button>
            )}
            {lastSale && (
              <button
                type="button"
                className="sb-lastsale"
                onClick={() => navigate('/pos/history')}
                title={t('Sotuvlar tarixi')}
              >
                ✓ #{lastSale.id} · {usd(lastSale.totalUzs)}
              </button>
            )}
          </span>
        </div>
      </div>

      {/* ============ RIGHT: register keys ============ */}
      <div className="ypos-side">
        <div className="ypos-side-top">
          <button
            type="button"
            className="ypos-home"
            onClick={() => navigate('/dashboard')}
            title={t('Asosiy menyuga qaytish')}
          >
            {AIco.home}
            <span>{t('Bosh menyu')}</span>
          </button>
          <button
            type="button"
            className={`ypos-rec ${voice.listening ? 'on' : ''}`}
            onClick={() => {
              if (!voice.supported) return;
              voice.listening ? voice.stop() : voice.start();
            }}
            title={voice.supported
              ? (voice.listening ? t('Tinglayman...') : t('Mikrofon orqali qidiruv'))
              : ''}
            aria-label={t('Mikrofon')}
          />
          <button
            type="button"
            className={`ypos-tool ${(pendingOffline > 0 || failedOffline > 0) ? 'has-queue' : ''}`}
            onClick={doSync}
            title={t('Sinxronlash')}
            aria-label={t('Sinxronlash')}
            disabled={syncBusy}
          >
            {AIco.sync}
          </button>
          <div className="ypos-menu-wrap">
            <button
              type="button"
              className="ypos-tool"
              onClick={() => setMenuOpen((v) => !v)}
              title={t('Menyu')}
              aria-label={t('Menyu')}
            >
              {AIco.burger}
            </button>
            {menuOpen && (
              <>
                <div className="ypos-menu-scrim" onClick={() => setMenuOpen(false)} />
                <div className="ypos-menu">
                  <button type="button" onClick={() => { setMenuOpen(false); navigate('/pos/history'); }}>
                    📋 {t('Sotuvlar tarixi')}
                  </button>
                  <button type="button" onClick={() => { setMenuOpen(false); newCheck(); }}>
                    🧾 {t('Yangi chek')}
                  </button>
                  <button type="button" onClick={() => { setMenuOpen(false); doSync(); }}>
                    🔄 {t('Sinxronlash')}
                  </button>
                  <button type="button" onClick={() => { setMenuOpen(false); openSyncCenter(); }}>
                    📤 {t('Offline navbat')} ({pendingOffline + failedOffline})
                  </button>
                  <button type="button" onClick={() => { setMenuOpen(false); setPayOpen(true); }}>
                    💰 {t("To'lov")} (F4)
                  </button>
                </div>
              </>
            )}
          </div>
        </div>

        <div className="ypos-actions">
          <button type="button" className="ypos-act danger" onClick={onDelete}>
            <span className="ico">{AIco.x}</span>
            {t("O'chirish")}
          </button>
          <button type="button" className="ypos-act" onClick={() => navigate('/pos/history')}>
            <span className="ico">{AIco.receipt}</span>
            {t('Cheklar')}
          </button>
          <button type="button" className="ypos-act" onClick={() => setClientOpen(true)}>
            <span className="ico">{AIco.person}</span>
            {t('Klient')}
          </button>
          <button type="button" className="ypos-act" onClick={() => setConfirmLock(true)}>
            <span className="ico">{AIco.lock}</span>
            {t('Bloklash')}
          </button>
        </div>

        <div className="ypos-pad">
          <button type="button" className="ypos-key" onClick={() => padPress('1')}>1</button>
          <button type="button" className="ypos-key" onClick={() => padPress('2')}>2</button>
          <button type="button" className="ypos-key" onClick={() => padPress('3')}>3</button>
          <button
            type="button"
            className={`ypos-mode ${padMode === 'miqdor' ? 'active' : ''}`}
            onClick={() => { setPadMode('miqdor'); setKeyboardOpen(false); }}
          >
            {t('Miqdor')}
          </button>

          <button type="button" className="ypos-key" onClick={() => padPress('4')}>4</button>
          <button type="button" className="ypos-key" onClick={() => padPress('5')}>5</button>
          <button type="button" className="ypos-key" onClick={() => padPress('6')}>6</button>
          <button
            type="button"
            className={`ypos-mode ${keyboardOpen ? 'active' : ''}`}
            onClick={toggleTouchKeyboard}
          >
            ABC
          </button>

          <button type="button" className="ypos-key" onClick={() => padPress('7')}>7</button>
          <button type="button" className="ypos-key" onClick={() => padPress('8')}>8</button>
          <button type="button" className="ypos-key" onClick={() => padPress('9')}>9</button>
          <button type="button" className="ypos-mode" onClick={focusSearch}>
            {t('Qidirish')}
          </button>

          <button type="button" className="ypos-key" onClick={() => padPress('00')}>00</button>
          <button type="button" className="ypos-key" onClick={() => padPress('0')}>0</button>
          <button type="button" className="ypos-key" onClick={() => padPress('.')}>.</button>
          <button
            type="button"
            className="ypos-mode clear"
            onClick={padClear}
            title={padMode === 'miqdor' ? t('Miqdorni tozalash') : t('Qidiruvni tozalash')}
          >
            ✕
          </button>
        </div>

        <button
          type="button"
          className="ypos-pay"
          onClick={() => { setKeyboardOpen(false); setPayOpen(true); }}
          disabled={cart.length === 0}
        >
          {t("To'lov")} · {usd(total)}
        </button>
      </div>

      {keyboardOpen && (
        <div className="ypos-touch-keyboard" aria-label={t('Ekran klaviaturasi')}>
          <div className="ytk-head">
            <b>ABC</b>
            <span className="ytk-query">{search || t('Qidirish...')}</span>
            <button type="button" onClick={() => setKeyboardOpen(false)}>
              {t('Yopish')}
            </button>
          </div>
          {TOUCH_KEY_ROWS.map((row) => (
            <div className="ytk-row" key={row.join('')}>
              {row.map((key) => (
                <button type="button" key={key} onClick={() => touchKeyPress(key)}>
                  {key}
                </button>
              ))}
            </div>
          ))}
          <div className="ytk-row ytk-actions">
            <button type="button" onClick={() => touchKeyPress(' ')} className="wide">
              {t("Bo'sh joy")}
            </button>
            <button type="button" onClick={touchBackspace}>{'<'}</button>
            <button type="button" onClick={() => setSearch('')}>{t('Tozalash')}</button>
            <button type="button" className="primary" onClick={submitSearch}>
              {t("Qo'shish")}
            </button>
          </div>
        </div>
      )}

      {syncOpen && (
        <Modal
          title={t('Offline navbat')}
          onClose={() => setSyncOpen(false)}
          wide
          footer={
            <>
              <button className="btn btn-ghost" onClick={() => setSyncOpen(false)}>
                {t('Yopish')}
              </button>
              <button
                className="btn btn-primary"
                onClick={() => runQueueFlush()}
                disabled={!online || syncBusy || pendingOffline === 0}
              >
                {syncBusy ? <Spinner /> : t('Hozir sinxronlash')}
              </button>
            </>
          }
        >
          <div className="offline-sync-panel">
            <div className="offline-sync-top">
              <div>
                <span>{t('Holat')}</span>
                <b>{online ? t('Online') : t('Offline')}</b>
              </div>
              <div>
                <span>{t('Navbat')}</span>
                <b>{pendingOffline}</b>
              </div>
              <div>
                <span>{t('Xato')}</span>
                <b>{failedOffline}</b>
              </div>
            </div>

            {queueRows.length === 0 ? (
              <div className="offline-sync-empty">
                {t('Offline navbat bo\'sh')}
              </div>
            ) : (
              <div className="offline-sync-list">
                {queueRows.map((row) => {
                  const summary = row.summary || {};
                  const items = summary.items || [];
                  const statusLabel = row.status === 'failed' ? t('Xato') : t('Navbatda');
                  const created = row.createdAt
                    ? new Date(row.createdAt).toLocaleString('uz-UZ', { hour12: false })
                    : '—';
                  return (
                    <div className={`offline-sync-row ${row.status}`} key={row.id}>
                      <div className="osr-head">
                        <div>
                          <b>{statusLabel}</b>
                          <span>{created}</span>
                        </div>
                        <strong className="mono">{summary.total != null ? usd(summary.total) : '—'}</strong>
                      </div>
                      <div className="osr-meta">
                        <span>{t("To'lov")}: {t(PAYMENT_METHODS.find((m) => m.value === summary.method)?.label || summary.method || '—')}</span>
                        {summary.customerName && <span>{t('Mijoz')}: {summary.customerName}</span>}
                        {row.attempts > 0 && <span>{t('Urinish')}: {row.attempts}</span>}
                      </div>
                      <div className="osr-items">
                        {items.slice(0, 5).map((it, idx) => (
                          <span key={`${row.id}-${it.productId}-${idx}`}>
                            {it.name || `#${it.productId}`} x{it.quantity}
                          </span>
                        ))}
                        {items.length > 5 && <span>+{items.length - 5}</span>}
                      </div>
                      {row.lastError && (
                        <div className="osr-error">{row.lastError}</div>
                      )}
                      <div className="osr-actions">
                        {row.status === 'failed' ? (
                          <button type="button" className="btn btn-sm" onClick={() => retryOfflineRow(row.id)}>
                            {t('Qayta navbatga')}
                          </button>
                        ) : (
                          <button
                            type="button"
                            className="btn btn-sm"
                            onClick={() => runQueueFlush()}
                            disabled={!online || syncBusy}
                          >
                            {t('Yuborish')}
                          </button>
                        )}
                        <button type="button" className="btn btn-sm btn-ghost" onClick={() => cancelOfflineRow(row.id)}>
                          {t('Bekor qilish')}
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </Modal>
      )}

      {/* ===== IMEI capture (shown before payment for IMEI-tracked lines) ===== */}
      {imeiLines && (
        <ImeiCaptureModal
          lines={imeiLines}
          onClose={() => setImeiLines(null)}
          onConfirm={(captured) => { setImeiLines(null); doCheckout(captured); }}
        />
      )}

      {/* ============ payment dialog ============ */}
      {payOpen && (
        <Modal
          title={`${t("To'lov")} — ${t('Chek')} № ${activeIdx + 1}`}
          onClose={() => setPayOpen(false)}
          footer={
            <>
              {/* Closing while busy is safe: the payload is snapshotted and
                  finishCheck(paidId) removes only the paid check, while the
                  busy guard on Sotish/F4 prevents a double submit. */}
              <button className="btn btn-ghost" onClick={() => setPayOpen(false)}>
                {t('Bekor qilish')}
              </button>
              <button
                className="btn btn-primary"
                onClick={doCheckout}
                disabled={busy || cart.length === 0}
              >
                {busy ? <Spinner /> : `💰 ${t('Sotish')} (F4)`}
              </button>
            </>
          }
        >
          <div className="ypos-pay-total">
            <span>{t('JAMI')}</span>
            <b className="mono">{usd(total)}</b>
          </div>
          <div className="ypos-pay-methods">
            {PAYMENT_METHODS.map((m) => (
              <button
                type="button"
                key={m.value}
                className={`ypos-pm ${method === m.value ? 'active' : ''}`}
                onClick={() => setMethod(m.value)}
              >
                {t(m.label)}
              </button>
            ))}
          </div>
          <div className="ypos-pay-grid">
            <div className="field" style={{ margin: 0 }}>
              <label>{t('Chegirma %')}</label>
              <input
                className="input" type="number" min="0" max="100"
                value={discountPercent}
                onChange={(e) => patchCheck({ discountPercent: e.target.value })}
              />
            </div>
            <div className="field" style={{ margin: 0 }}>
              <label>{t('Chegirma summa (USD)')}</label>
              <input
                className="input" type="number" min="0"
                value={discountAmount}
                onChange={(e) => patchCheck({ discountAmount: e.target.value })}
              />
            </div>
            <div className="field" style={{ margin: 0 }}>
              <label>
                {t('Mijoz')} ({t('ixtiyoriy')})
                {(() => {
                  const sel = (customers || []).find(
                    (c) => String(c.id) === String(activeCheck.customerId));
                  return sel && Number(sel.pointsBalance) > 0
                    ? <span style={{ marginLeft: 6, fontWeight: 700, color: '#d97706' }}>
                        ⭐ {Number(sel.pointsBalance).toLocaleString()} {t('ball')}
                      </span>
                    : null;
                })()}
              </label>
              <select
                className="input"
                value={activeCheck.customerId}
                onChange={(e) => patchCheck({ customerId: e.target.value })}
              >
                <option value="">—</option>
                {(customers || []).map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}{Number(c.pointsBalance) > 0 ? ` (⭐${c.pointsBalance})` : ''}
                  </option>
                ))}
              </select>
            </div>
            <div className="field" style={{ margin: 0 }}>
              <label>{t('Izoh')} ({t('ixtiyoriy')})</label>
              <input
                className="input"
                value={activeCheck.note}
                onChange={(e) => patchCheck({ note: e.target.value })}
              />
            </div>
          </div>
          <div className="ypos-pay-rows">
            <div><span>{t('Subtotal')}</span><span className="mono">{usd(subtotal)}</span></div>
            {lineDiscountTotal > 0 && (
              <div className="muted">
                <span>{t('Chiziq chegirmalari')}</span>
                <span className="mono">- {usd(lineDiscountTotal)}</span>
              </div>
            )}
            {(Number(discountAmount) > 0 || percent > 0) && (
              <div className="muted">
                <span>{t('Chegirma')}</span>
                <span className="mono">- {usd(afterLine - total)}</span>
              </div>
            )}
          </div>
        </Modal>
      )}

      {/* ============ customer picker (Klient) ============ */}
      {clientOpen && (
        <Modal title={t('Mijozni tanlash')} onClose={() => setClientOpen(false)}>
          <input
            className="input"
            placeholder={t('Qidirish...')}
            value={clientQuery}
            onChange={(e) => setClientQuery(e.target.value)}
            autoFocus
          />
          <div className="ypos-client-list">
            <button
              type="button"
              className={!activeCheck.customerId ? 'active' : ''}
              onClick={() => { patchCheck({ customerId: '' }); setClientOpen(false); }}
            >
              — {t('Mijozsiz')}
            </button>
            {filteredClients.map((c) => (
              <button
                type="button"
                key={c.id}
                className={String(activeCheck.customerId) === String(c.id) ? 'active' : ''}
                onClick={() => { patchCheck({ customerId: String(c.id) }); setClientOpen(false); }}
              >
                {c.name}
                {c.phone && <span className="ph">{c.phone}</span>}
              </button>
            ))}
          </div>
        </Modal>
      )}

      {confirmClear && (
        <ConfirmDialog
          title={t('Chekni tozalash')}
          message={`${t('Chek')} № ${activeIdx + 1} — ${cart.length} ${t('ta mahsulot o\'chiriladi')}.`}
          confirmLabel={t("O'chirish")}
          onConfirm={() => {
            // Clearing means "start this tab over": drop the sale-wide
            // discount/customer/note too, or the next sale inherits them.
            patchCheck({
              cart: [], discountPercent: '', discountAmount: '',
              customerId: '', note: '',
            });
            setSelectedLine(null);
            setQtyBuf('');
            setConfirmClear(false);
          }}
          onCancel={() => setConfirmClear(false)}
        />
      )}

      {confirmLock && (
        <ConfirmDialog
          title={t('Bloklash')}
          message={t('Ekran bloklanadi — davom etish uchun qayta kirish kerak bo\'ladi.')}
          confirmLabel={t('Bloklash')}
          onConfirm={logout}
          onCancel={() => setConfirmLock(false)}
        />
      )}
    </div>
  );
}
