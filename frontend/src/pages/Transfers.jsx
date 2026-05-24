import { useEffect, useMemo, useState } from 'react';
import { ProductApi, ShopApi, TransferApi } from '../api/endpoints.js';
import { Modal } from '../components/Modal.jsx';
import { useToast } from '../components/Toast.jsx';
import { useAuth } from '../context/Auth.jsx';
import { useT } from '../context/Settings.jsx';
import { EmptyState, Loader, PageHeader } from '../components/ui.jsx';
import { formatDate, formatMoney } from '../lib/format.js';

/**
 * "Tovar transferi" — move stock between two shops of the same
 * account. Top action button opens the new-transfer modal; the
 * table underneath is the audit trail of previous transfers.
 *
 * Owner-only by design: cashiers shouldn't be moving inventory
 * across locations on their own.
 */
export function Transfers() {
  const t = useT();
  const toast = useToast();
  const { user } = useAuth();

  const [shops, setShops] = useState([]);
  const [list, setList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [creating, setCreating] = useState(false);

  const canCreate = user?.role === 'ACCOUNT_OWNER' || user?.role === 'SUPER_ADMIN';

  const reload = async () => {
    setLoading(true);
    setError(null);
    try {
      const [s, l] = await Promise.all([ShopApi.list(), TransferApi.list()]);
      setShops(s);
      setList(l);
    } catch (err) {
      setError(err.message || t('Yuklab bo\'lmadi'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { reload(); }, []);   // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="page">
      <PageHeader
        title={t('Tovar transferi')}
        subtitle={t("Bir do'kondan boshqasiga zaxira ko'chirish")}
        actions={canCreate && shops.length >= 2 ? (
          <button className="btn btn-primary" onClick={() => setCreating(true)}>
            + {t('Yangi transfer')}
          </button>
        ) : null}
      />
      {!canCreate && (
        <div className="muted" style={{ fontSize: 13, marginBottom: 12 }}>
          ℹ {t("Faqat akkaunt egasi transfer ochishi mumkin")}
        </div>
      )}
      {canCreate && shops.length < 2 && (
        <div className="muted" style={{ fontSize: 13, marginBottom: 12 }}>
          ⚠ {t("Transfer uchun kamida 2 ta do'kon kerak")}
        </div>
      )}

      <Loader loading={loading} error={error} onRetry={reload}>
        {list.length === 0 ? (
          <EmptyState
            icon="🔁"
            text={t("Hozircha transfer yo'q")}
            hint={t("Yangi transfer tugmasini bosib boshlang")}
          />
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>{t('Sana')}</th>
                <th>{t("Manba")}</th>
                <th>{t("Qabul")}</th>
                <th>{t('Mahsulot')}</th>
                <th style={{ textAlign: 'right' }}>{t('Miqdor')}</th>
                <th>{t('Izoh')}</th>
                <th>{t('Kim')}</th>
              </tr>
            </thead>
            <tbody>
              {list.map((r) => (
                <tr key={r.id}>
                  <td className="mono" style={{ fontSize: 12 }}>
                    {(r.createdAt || '').replace('T', ' ').slice(0, 16)}
                  </td>
                  <td>{r.fromShopName}</td>
                  <td>{r.toShopName}</td>
                  <td>
                    <strong>{r.productName}</strong>
                    {r.productBarcode && (
                      <div className="muted mono" style={{ fontSize: 11 }}>
                        {r.productBarcode}
                      </div>
                    )}
                  </td>
                  <td className="mono" style={{ textAlign: 'right' }}>
                    {Number(r.qty).toLocaleString()}
                  </td>
                  <td className="muted">{r.note || '—'}</td>
                  <td className="muted">{r.createdBy || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Loader>

      {creating && (
        <TransferModal
          shops={shops}
          onClose={() => setCreating(false)}
          onDone={() => { setCreating(false); reload(); toast.success(t('Transfer saqlandi')); }}
        />
      )}
    </div>
  );
}

function TransferModal({ shops, onClose, onDone }) {
  const t = useT();
  const [fromShopId, setFromShopId] = useState(shops[0]?.id || '');
  const [toShopId, setToShopId] = useState(shops[1]?.id || shops[0]?.id || '');
  const [products, setProducts] = useState([]);
  const [productsLoading, setProductsLoading] = useState(false);
  const [productId, setProductId] = useState('');
  const [qty, setQty] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  // Source-shop products. We re-fetch every time the source shop
  // changes because ProductApi.list scopes to the active X-Shop-Id
  // header — overriding it just for this picker would require a
  // bespoke endpoint, so we use a one-shot fetch with a temporary
  // header swap via storage.
  useEffect(() => {
    if (!fromShopId) {
      setProducts([]);
      return undefined;
    }
    let cancelled = false;
    setProductsLoading(true);
    const previousShop = localStorage.getItem('savdopro.activeShopId');
    localStorage.setItem('savdopro.activeShopId', String(fromShopId));
    ProductApi.list()
      .then((rows) => { if (!cancelled) setProducts(rows || []); })
      .catch(() => { if (!cancelled) setProducts([]); })
      .finally(() => {
        if (!cancelled) setProductsLoading(false);
        // Restore the user's actual active shop so other in-flight
        // requests aren't accidentally re-scoped.
        if (previousShop) localStorage.setItem('savdopro.activeShopId', previousShop);
        else localStorage.removeItem('savdopro.activeShopId');
      });
    return () => { cancelled = true; };
  }, [fromShopId]);

  const product = useMemo(
    () => products.find((p) => String(p.id) === String(productId)),
    [products, productId],
  );

  const submit = async () => {
    setError('');
    if (!fromShopId || !toShopId) {
      setError(t("Manba va qabul do'konni tanlang")); return;
    }
    if (String(fromShopId) === String(toShopId)) {
      setError(t("Manba va qabul do'koni bir xil bo'lmasligi kerak")); return;
    }
    if (!productId) { setError(t('Mahsulotni tanlang')); return; }
    const qtyNum = Number(qty);
    if (!Number.isFinite(qtyNum) || qtyNum <= 0) {
      setError(t("Miqdor 0 dan katta bo'lishi kerak")); return;
    }
    if (product && qtyNum > Number(product.stockQty)) {
      setError(`${t("Yetarli zaxira yo'q")}: ${product.stockQty}`); return;
    }
    setBusy(true);
    try {
      await TransferApi.create({
        fromShopId: Number(fromShopId),
        toShopId: Number(toShopId),
        sourceProductId: Number(productId),
        qty: qtyNum,
        note: note.trim() || null,
      });
      onDone();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <Modal
      title={t('Yangi transfer')}
      onClose={onClose}
      footer={(
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={busy}>
            {t('Bekor qilish')}
          </button>
          <button className="btn btn-primary" onClick={submit} disabled={busy}>
            {busy ? t('Saqlanmoqda...') : t("Ko'chirish")}
          </button>
        </>
      )}
    >
      <div className="form-row">
        <div className="field">
          <label>{t("Manba do'kon")} *</label>
          <select className="input" value={fromShopId}
                  onChange={(e) => { setFromShopId(e.target.value); setProductId(''); }}>
            {shops.map((s) => (
              <option key={s.id} value={s.id}>{s.name}{s.main ? ' ★' : ''}</option>
            ))}
          </select>
        </div>
        <div className="field">
          <label>{t("Qabul do'kon")} *</label>
          <select className="input" value={toShopId}
                  onChange={(e) => setToShopId(e.target.value)}>
            {shops.map((s) => (
              <option key={s.id} value={s.id}>{s.name}{s.main ? ' ★' : ''}</option>
            ))}
          </select>
        </div>
      </div>
      <div className="field">
        <label>{t('Mahsulot')} *</label>
        <select className="input" value={productId}
                onChange={(e) => setProductId(e.target.value)}
                disabled={productsLoading}>
          <option value="">
            {productsLoading ? t('Yuklanmoqda...') : t('— tanlang —')}
          </option>
          {products.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name} {p.barcode ? `(${p.barcode}) ` : ''}— {t('mavjud')}: {p.stockQty}
            </option>
          ))}
        </select>
      </div>
      <div className="form-row">
        <div className="field">
          <label>{t('Miqdor')} *</label>
          <input className="input" type="number" min="0" step="0.001"
                 value={qty} onChange={(e) => setQty(e.target.value)} />
          {product && (
            <div className="muted" style={{ fontSize: 11, marginTop: 4 }}>
              {t('Manbada')}: <b>{product.stockQty}</b> ·{' '}
              {t('Narx')}: {formatMoney(product.salePrice)}
            </div>
          )}
        </div>
        <div className="field">
          <label>{t('Izoh')}</label>
          <input className="input" value={note}
                 onChange={(e) => setNote(e.target.value)}
                 placeholder={t("majburiy emas")} />
        </div>
      </div>
      {error && (
        <div style={{ color: 'var(--red)', fontSize: 12, marginTop: 8 }}>
          ⚠ {error}
        </div>
      )}
    </Modal>
  );
}
