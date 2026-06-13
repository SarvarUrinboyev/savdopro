import { useEffect, useState } from 'react';
import { PosApi } from '../api/endpoints.js';
import { ExportButton } from '../components/ExportButton.jsx';
import { Modal } from '../components/Modal.jsx';
import { useToast } from '../components/Toast.jsx';
import { EmptyState, Loader, PageHeader, Spinner } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { usd } from '../lib/format.js';

/**
 * POS sales history + refund flow.
 *
 * Table of the last 100 sales with status badges (FAOL / QISMAN QAYTARILGAN
 * / TO'LIQ QAYTARILGAN). Clicking a row opens the receipt detail with
 * per-line refund controls.
 */
export function PosHistory({ embedded = false }) {
  const t = useT();
  const PAGE_SIZE = 50;
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(null);
  const [open, setOpen] = useState(null); // {sale}
  const [sendingId, setSendingId] = useState(null);
  const toast = useToast();
  const rows = items;

  const sendReceipt = async (sale) => {
    setSendingId(sale.id);
    try {
      const r = await PosApi.sendReceipt(sale.id);
      const label = r.channel === 'TELEGRAM' ? 'Telegram'
        : r.channel === 'SMS' ? 'SMS' : null;
      if (label) {
        toast.success(`${t('Chek yuborildi')} (${label})`);
      } else {
        toast.error(t("Kanal yo'q: mijoz Telegram botga ulanmagan va SMS sozlanmagan"));
      }
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSendingId(null);
    }
  };

  const fetchPage = async (p, append) => {
    if (append) setLoadingMore(true); else setLoading(true);
    setError(null);
    try {
      const res = await PosApi.recent(p, PAGE_SIZE);
      const next = res?.items || [];
      setItems((prev) => (append ? [...prev, ...next] : next));
      setHasMore(Boolean(res?.hasMore));
      setPage(p);
    } catch (err) {
      setError(err.message || t('Xatolik yuz berdi'));
    } finally {
      if (append) setLoadingMore(false); else setLoading(false);
    }
  };

  useEffect(() => { fetchPage(0, false); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const reload = () => fetchPage(0, false);
  const loadMore = () => fetchPage(page + 1, true);

  const exportBtn = (
    <ExportButton
      filename={`sotuvlar-${new Date().toISOString().slice(0, 10)}`}
      rows={rows.map((s) => ({
        [t('Sotuv')]: `#${s.id}`,
        [t('Vaqt')]: s.createdAt,
        [t('Mijoz')]: s.customerName || '',
        [t('Tovar dona')]: s.items.length,
        [t('Subtotal')]: Number(s.subtotalUzs),
        [t('Chegirma')]: Number(s.subtotalUzs) - Number(s.totalUzs),
        [t("To'lov turi")]: s.paymentMethod,
        [t('Jami USD')]: Number(s.totalUzs),
        [t('Qaytarilgan')]: Number(s.refundedTotalUzs),
        [t('Holat')]: s.fullyRefunded ? 'TO_LIQ QAYTARILGAN'
          : (s.refundedTotalUzs > 0 ? 'QISMAN' : 'FAOL'),
      }))}
    />
  );

  const body = (
    <Loader loading={loading} error={error} onRetry={reload}>
      {rows.length === 0 ? (
        <EmptyState icon="🛒" text={t("Hali sotuv yo'q")} />
      ) : (
        <div className="table-wrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>#</th>
                <th>{t('Vaqt')}</th>
                <th>{t('Mijoz')}</th>
                <th className="num">{t('Tovar')}</th>
                <th>{t("To'lov turi")}</th>
                <th className="num">{t('Jami')}</th>
                <th className="num">{t('Qaytarilgan')}</th>
                <th>{t('Holat')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((s) => (
                <tr key={s.id} className="hover-row">
                  <td className="mono"><strong>#{s.id}</strong></td>
                  <td className="faint mono" style={{ whiteSpace: 'nowrap' }}>
                    {formatTs(s.createdAt)}
                  </td>
                  <td>{s.customerName || <span className="faint">—</span>}</td>
                  <td className="num mono">{s.items.length}</td>
                  <td><span className="badge">{s.paymentMethod}</span></td>
                  <td className="num mono"><strong>{usd(s.totalUzs)}</strong></td>
                  <td className="num mono faint">
                    {Number(s.refundedTotalUzs) > 0 ? usd(s.refundedTotalUzs) : '—'}
                  </td>
                  <td>
                    {s.fullyRefunded
                      ? <span className="badge badge-qarzga">{t('Qaytarilgan')}</span>
                      : Number(s.refundedTotalUzs) > 0
                        ? <span className="badge badge-aralash">{t('Qisman')}</span>
                        : <span className="badge badge-naqd">{t('Faol')}</span>}
                  </td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    {s.customerId && (
                      <button
                        className="btn btn-ghost btn-sm"
                        disabled={sendingId === s.id}
                        title={t('Chekni mijozga yuborish (Telegram/SMS)')}
                        onClick={() => sendReceipt(s)}
                      >
                        {sendingId === s.id ? '⏳' : '📨'}
                      </button>
                    )}
                    <button className="btn btn-ghost btn-sm" onClick={() => setOpen({ sale: s })}>
                      {t('Batafsil')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {hasMore && rows.length > 0 && (
        <div style={{ textAlign: 'center', marginTop: 16 }}>
          <button className="btn btn-ghost" onClick={loadMore} disabled={loadingMore}>
            {loadingMore ? t('Yuklanmoqda...') : `⬇️ ${t("Ko'proq yuklash")}`}
          </button>
        </div>
      )}
    </Loader>
  );

  const modal = open && (
    <SaleDetailModal
      sale={open.sale}
      onClose={() => setOpen(null)}
      onChanged={() => { setOpen(null); reload(); }}
    />
  );

  // Embedded into the Hisobotlar page as a card section; standalone (reached
  // from the Kassa page) keeps its own page header.
  if (embedded) {
    return (
      <div className="card">
        <div className="card-head">
          <h2>🧾 {t('Sotuvlar tarixi')}</h2>
          <span className="hint">{rows.length}{hasMore ? '+' : ''} {t('ta sotuv')}</span>
        </div>
        <div className="card-pad">
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12 }}>
            {exportBtn}
          </div>
          {body}
        </div>
        {modal}
      </div>
    );
  }

  return (
    <>
      <PageHeader title={t('Sotuvlar tarixi')} desc={`${rows.length}${hasMore ? '+' : ''} ${t('ta sotuv')}`}>
        {exportBtn}
      </PageHeader>
      <div className="card section">{body}</div>
      {modal}
    </>
  );
}

function SaleDetailModal({ sale, onClose, onChanged }) {
  const t = useT();
  const toast = useToast();
  // refund qty per saleItemId
  const [refundQty, setRefundQty] = useState(() => {
    const init = {};
    sale.items.forEach((it) => { init[it.id] = 0; });
    return init;
  });
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  const refundTotal = sale.items.reduce((s, it) => {
    const qty = refundQty[it.id] || 0;
    const unitNet = it.quantity === 0 ? 0 : Number(it.lineTotalUzs) / it.quantity;
    return s + unitNet * qty;
  }, 0);

  const doRefund = async (allRemaining) => {
    setBusy(true);
    try {
      const items = allRemaining
        ? []
        : sale.items
            .filter((it) => (refundQty[it.id] || 0) > 0)
            .map((it) => ({ saleItemId: it.id, quantity: refundQty[it.id] }));
      if (!allRemaining && items.length === 0) {
        toast.error(t('Hech narsa tanlanmagan'));
        setBusy(false);
        return;
      }
      await PosApi.refund(sale.id, { items, reason: reason || null });
      toast.success(t('Qaytarish bajarildi'));
      onChanged();
    } catch (err) {
      toast.error(err.message || t("Qaytarib bo'lmadi"));
    } finally {
      setBusy(false);
    }
  };

  const hasRemaining = sale.items.some((it) => it.quantity - it.refundedQty > 0);

  return (
    <Modal title={`${t('Sotuv')} #${sale.id}`} onClose={onClose} size="lg">
      <div className="faint" style={{ fontSize: 13, marginBottom: 12 }}>
        {formatTs(sale.createdAt)} · {sale.paymentMethod}
        {sale.customerName && <> · {sale.customerName}</>}
      </div>

      <div className="table-wrap">
        <table className="tbl">
          <thead>
            <tr>
              <th>{t('Mahsulot')}</th>
              <th className="num">{t('Dona')}</th>
              <th className="num">{t('Narx')}</th>
              <th className="num">{t('Jami')}</th>
              <th className="num">{t('Qaytarilgan')}</th>
              {hasRemaining && !sale.fullyRefunded && (
                <th className="num">{t('Qaytarish')}</th>
              )}
            </tr>
          </thead>
          <tbody>
            {sale.items.map((it) => {
              const remaining = it.quantity - it.refundedQty;
              return (
                <tr key={it.id}>
                  <td><strong>{it.productName}</strong>
                    {it.productSku && <div className="faint mono" style={{ fontSize: 11 }}>{it.productSku}</div>}
                  </td>
                  <td className="num mono">{it.quantity}</td>
                  <td className="num mono">{usd(it.unitPriceUzs)}</td>
                  <td className="num mono">{usd(it.lineTotalUzs)}</td>
                  <td className="num mono faint">{it.refundedQty || '—'}</td>
                  {hasRemaining && !sale.fullyRefunded && (
                    <td className="num">
                      {remaining > 0 ? (
                        <input
                          type="number" className="input" min="0" max={remaining}
                          value={refundQty[it.id] || 0}
                          onChange={(e) => setRefundQty((p) => ({
                            ...p, [it.id]: Math.max(0, Math.min(remaining, Number(e.target.value) || 0)),
                          }))}
                          style={{ width: 70 }}
                        />
                      ) : <span className="faint">—</span>}
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <div style={{ marginTop: 16, padding: 12, background: '#f9fafb', borderRadius: 8 }}>
        <Row label={t('Subtotal')} value={usd(sale.subtotalUzs)} />
        {(Number(sale.discountAmount) > 0 || Number(sale.discountPercent) > 0) && (
          <Row label={t('Chegirma')} value={`%${sale.discountPercent} + ${usd(sale.discountAmount)}`} muted />
        )}
        <Row label={t('JAMI')} value={usd(sale.totalUzs)} big />
        {Number(sale.refundedTotalUzs) > 0 && (
          <Row label={t('Allaqachon qaytarilgan')} value={`- ${usd(sale.refundedTotalUzs)}`} muted />
        )}
      </div>

      {hasRemaining && !sale.fullyRefunded && (
        <>
          <div className="field" style={{ marginTop: 12 }}>
            <label>{t('Qaytarish sababi')} ({t('ixtiyoriy')})</label>
            <input className="input" value={reason} onChange={(e) => setReason(e.target.value)} />
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 12 }}>
            <div className="faint" style={{ fontSize: 13 }}>
              {t('Tanlangan summa')}: <strong>{usd(refundTotal)}</strong>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button className="btn btn-ghost" onClick={() => doRefund(true)} disabled={busy}>
                {busy ? <Spinner /> : `↩️ ${t("Qolganini to'liq qaytarish")}`}
              </button>
              <button
                className="btn btn-primary"
                onClick={() => doRefund(false)}
                disabled={busy || refundTotal === 0}
              >
                {busy ? <Spinner /> : `↩️ ${t('Qaytarish')}`}
              </button>
            </div>
          </div>
        </>
      )}
    </Modal>
  );
}

function Row({ label, value, big, muted }) {
  return (
    <div style={{
      display: 'flex', justifyContent: 'space-between',
      fontSize: big ? 16 : 13, fontWeight: big ? 700 : 500,
      color: muted ? '#9ca3af' : '#111827',
      marginTop: big ? 6 : 2,
      borderTop: big ? '1px solid #e5e7eb' : 'none',
      paddingTop: big ? 6 : 0,
    }}>
      <span>{label}</span><span className="mono">{value}</span>
    </div>
  );
}

function formatTs(iso) {
  if (!iso) return '—';
  const m = String(iso).match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})/);
  return m ? `${m[3]}.${m[2]} ${m[4]}:${m[5]}` : iso;
}
