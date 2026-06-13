import { useState } from 'react';
import { CostingApi, ProductApi, PurchaseOrderApi, SupplierApi } from '../api/endpoints.js';
import { ConfirmDialog, Modal } from '../components/Modal.jsx';
import { useToast } from '../components/Toast.jsx';
import { CurrencyToggle, EmptyState, Loader, PageHeader } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { useExchangeRate } from '../hooks/useExchangeRate.js';
import { useStickyState } from '../hooks/useStickyState.js';
import { convertMoney, formatDate, formatMoney, todayIso } from '../lib/format.js';

const STATUS_LABEL = {
  DRAFT: 'Qoralama', ORDERED: 'Buyurtma berilgan', PARTIAL: 'Qisman kelgan',
  RECEIVED: 'To‘liq kelgan', CANCELLED: 'Bekor qilingan',
};
const STATUS_BADGE = {
  DRAFT: 'badge-muted', ORDERED: 'badge-karta', PARTIAL: 'badge-aralash',
  RECEIVED: 'badge-naqd', CANCELLED: 'badge-qarzga',
};

/** Supplier purchase orders: order → (partial) receive → received, with cost layers. */
export function PurchaseOrders() {
  const t = useT();
  const toast = useToast();
  const rate = useExchangeRate();
  const [cur, setCur] = useStickyState('barakat.cur.acct', 'USD');
  const [modal, setModal] = useState(null);

  const { data, loading, error, reload } = useApi(() => PurchaseOrderApi.list(), []);
  const products = useApi(() => ProductApi.list(), []);
  const suppliers = useApi(() => SupplierApi.list(), []);

  const fmt = (u) => formatMoney(convertMoney(u, 'USD', cur, rate), cur);

  const act = async (fn, ok) => {
    try { await fn(); toast.success(t(ok)); setModal(null); reload(); }
    catch (err) { toast.error(err.message); }
  };

  return (
    <>
      <PageHeader title={t('Yetkazib beruvchi buyurtmalari')}
                  desc={t('Buyurtma, invoice, qabul qilish va kelish narxi tarixi')}>
        <CurrencyToggle value={cur} onChange={setCur} />
        <button className="btn btn-ghost" onClick={() => setModal({ type: 'valuation' })}>
          📊 {t('Tannarx baholash')}
        </button>
        <button className="btn btn-primary" onClick={() => setModal({ type: 'create' })}>
          + {t('Yangi buyurtma')}
        </button>
      </PageHeader>

      <div className="card">
        <div className="card-head"><h2>{t('Buyurtmalar')}</h2>
          <span className="hint">{data?.length || 0} {t('ta')}</span></div>
        <Loader loading={loading} error={error} onRetry={reload}>
          {!data || data.length === 0 ? (
            <EmptyState icon="📦" text={t('Buyurtma yo‘q')} />
          ) : (
            <div className="table-wrap">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>{t('Yetkazib beruvchi')}</th>
                    <th>{t('Holat')}</th>
                    <th>{t('Invoice')}</th>
                    <th className="num">{t('Buyurtma')}</th>
                    <th className="num">{t('Kelgan')}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.map((po) => (
                    <tr key={po.id}>
                      <td className="name-cell">{po.supplierName}
                        <div className="faint" style={{ fontSize: 11 }}>
                          {po.orderDate ? formatDate(po.orderDate) : t('sana yo‘q')}
                          {po.expectedDate ? ` → ${formatDate(po.expectedDate)}` : ''}
                        </div>
                      </td>
                      <td><span className={`badge ${STATUS_BADGE[po.status]}`}>
                        {t(STATUS_LABEL[po.status] || po.status)}</span></td>
                      <td className="faint">{po.invoiceNumber || '—'}</td>
                      <td className="num mono">{fmt(po.orderedTotalUsd)}</td>
                      <td className="num mono">{fmt(po.receivedTotalUsd)}</td>
                      <td className="right">
                        <div className="row-actions">
                          {po.status === 'DRAFT' && (
                            <button className="btn btn-ghost btn-sm"
                                    onClick={() => act(() => PurchaseOrderApi.order(po.id), 'Buyurtma berildi')}>
                              📨 {t('Buyurtma berish')}
                            </button>
                          )}
                          {(po.status === 'ORDERED' || po.status === 'PARTIAL' || po.status === 'DRAFT') && (
                            <button className="btn btn-primary btn-sm"
                                    onClick={() => setModal({ type: 'receive', po })}>
                              📥 {t('Qabul qilish')}
                            </button>
                          )}
                          {po.status === 'DRAFT' && (
                            <button className="icon-btn" title={t('Tahrirlash')}
                                    onClick={() => setModal({ type: 'edit', po })}>✏️</button>
                          )}
                          {po.status !== 'RECEIVED' && po.status !== 'CANCELLED' && (
                            <button className="icon-btn" title={t('Bekor qilish')}
                                    onClick={() => setModal({ type: 'cancel', po })}>🚫</button>
                          )}
                          {(po.status === 'DRAFT' || po.status === 'CANCELLED') && (
                            <button className="icon-btn danger" title={t("O'chirish")}
                                    onClick={() => setModal({ type: 'delete', po })}>🗑</button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Loader>
      </div>

      {(modal?.type === 'create' || modal?.type === 'edit') && (
        <PoFormModal
          initial={modal.type === 'edit' ? modal.po : null}
          products={products.data || []}
          suppliers={suppliers.data || []}
          onSubmit={async (body) => {
            if (modal.type === 'edit') await PurchaseOrderApi.update(modal.po.id, body);
            else await PurchaseOrderApi.create(body);
            toast.success(t('Saqlandi'));
            reload();
          }}
          onClose={() => setModal(null)}
        />
      )}

      {modal?.type === 'receive' && (
        <ReceiveModal
          po={modal.po}
          onSubmit={async (body) => {
            await PurchaseOrderApi.receive(modal.po.id, body);
            toast.success(t('Tovar qabul qilindi'));
            reload();
            products.reload?.();
          }}
          onClose={() => setModal(null)}
        />
      )}

      {modal?.type === 'cancel' && (
        <ConfirmDialog title={t('Buyurtmani bekor qilish')}
          message={t('Bu buyurtmani bekor qilmoqchimisiz?')} confirmLabel={t('Bekor qilish')}
          onConfirm={() => act(() => PurchaseOrderApi.cancel(modal.po.id), 'Buyurtma bekor qilindi')}
          onCancel={() => setModal(null)} />
      )}
      {modal?.type === 'delete' && (
        <ConfirmDialog title={t("Buyurtmani o'chirish")}
          message={t("Buyurtmani o'chirmoqchimisiz?")} confirmLabel={t("O'chirish")}
          onConfirm={() => act(() => PurchaseOrderApi.remove(modal.po.id), "Buyurtma o'chirildi")}
          onCancel={() => setModal(null)} />
      )}
      {modal?.type === 'valuation' && (
        <ValuationModal fmt={fmt} onClose={() => setModal(null)} />
      )}
    </>
  );
}

const blankLine = () => ({ productId: '', orderedQty: '', unitCostUsd: '' });

function PoFormModal({ initial, products, suppliers, onSubmit, onClose }) {
  const t = useT();
  const [supplierId, setSupplierId] = useState(initial?.supplierId ? String(initial.supplierId) : '');
  const [orderDate, setOrderDate] = useState(initial?.orderDate ?? todayIso());
  const [expectedDate, setExpectedDate] = useState(initial?.expectedDate ?? '');
  const [invoiceNumber, setInvoiceNumber] = useState(initial?.invoiceNumber ?? '');
  const [note, setNote] = useState(initial?.note ?? '');
  const [lines, setLines] = useState(() => initial?.lines?.length
    ? initial.lines.map((l) => ({ productId: String(l.productId ?? ''), orderedQty: String(l.orderedQty), unitCostUsd: String(l.unitCostUsd) }))
    : [blankLine()]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const setLine = (i, patch) => setLines((a) => a.map((l, idx) => (idx === i ? { ...l, ...patch } : l)));
  const onPickProduct = (i, pid) => {
    const p = products.find((x) => String(x.id) === pid);
    setLine(i, { productId: pid, unitCostUsd: p ? String(p.purchasePrice ?? '') : '' });
  };

  const submit = async () => {
    const supplier = suppliers.find((s) => String(s.id) === supplierId);
    if (!supplier) { setError(t('Yetkazib beruvchi tanlang')); return; }
    const filled = lines.filter((l) => l.productId && Number(l.orderedQty) > 0);
    if (filled.length === 0) { setError(t('Kamida bitta mahsulot qatori kerak')); return; }
    setBusy(true);
    try {
      await onSubmit({
        supplierId: Number(supplierId),
        supplierName: supplier.name,
        orderDate: orderDate || null,
        expectedDate: expectedDate || null,
        invoiceNumber: invoiceNumber.trim() || null,
        note: note.trim() || null,
        lines: filled.map((l) => ({
          productId: Number(l.productId),
          orderedQty: Number(l.orderedQty),
          unitCostUsd: Number(l.unitCostUsd) || 0,
        })),
      });
      onClose();
    } catch (err) { setError(err.message); setBusy(false); }
  };

  return (
    <Modal title={initial ? t('Buyurtmani tahrirlash') : t('Yangi buyurtma')} wide onClose={onClose}
      footer={<>
        <button className="btn btn-ghost" onClick={onClose} disabled={busy}>{t('Bekor qilish')}</button>
        <button className="btn btn-primary" onClick={submit} disabled={busy}>
          {busy ? t('Saqlanmoqda...') : t('Saqlash')}</button>
      </>}>
      <div className="form-row">
        <div className="field">
          <label>{t('Yetkazib beruvchi')} *</label>
          <select className="select" value={supplierId} onChange={(e) => setSupplierId(e.target.value)}>
            <option value="">{t('Tanlang...')}</option>
            {suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </div>
        <div className="field">
          <label>{t('Invoice raqami')}</label>
          <input className="input" value={invoiceNumber} onChange={(e) => setInvoiceNumber(e.target.value)} />
        </div>
      </div>
      <div className="form-row">
        <div className="field">
          <label>{t('Buyurtma sanasi')}</label>
          <input className="input" type="date" value={orderDate} onChange={(e) => setOrderDate(e.target.value)} />
        </div>
        <div className="field">
          <label>{t('Kutilayotgan sana')}</label>
          <input className="input" type="date" value={expectedDate} onChange={(e) => setExpectedDate(e.target.value)} />
        </div>
      </div>

      <div className="table-wrap" style={{ marginTop: 8 }}>
        <table className="tbl">
          <thead><tr><th>{t('Mahsulot')}</th><th className="num">{t('Miqdor')}</th>
            <th className="num">{t('Kelish narxi')}</th><th /></tr></thead>
          <tbody>
            {lines.map((l, i) => (
              <tr key={i}>
                <td>
                  <select className="select" value={l.productId} onChange={(e) => onPickProduct(i, e.target.value)}>
                    <option value="">{t('Tanlang...')}</option>
                    {products.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                  </select>
                </td>
                <td><input className="input num" type="number" value={l.orderedQty}
                           onChange={(e) => setLine(i, { orderedQty: e.target.value })} placeholder="0" /></td>
                <td><input className="input num" type="number" value={l.unitCostUsd}
                           onChange={(e) => setLine(i, { unitCostUsd: e.target.value })} placeholder="0" /></td>
                <td><button className="icon-btn danger" onClick={() => setLines((a) => a.length > 1 ? a.filter((_, idx) => idx !== i) : a)}>✕</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <button className="btn btn-ghost btn-sm" style={{ marginTop: 8 }}
              onClick={() => setLines((a) => [...a, blankLine()])}>+ {t('Qator qo‘shish')}</button>
      <div className="field" style={{ marginTop: 8 }}>
        <label>{t('Izoh')}</label>
        <input className="input" value={note} onChange={(e) => setNote(e.target.value)} />
      </div>
      {error && <div style={{ color: 'var(--red)', fontSize: 12, marginTop: 8 }}>{error}</div>}
    </Modal>
  );
}

function ReceiveModal({ po, onSubmit, onClose }) {
  const t = useT();
  const [receiptDate, setReceiptDate] = useState(todayIso());
  const [invoiceNumber, setInvoiceNumber] = useState(po.invoiceNumber ?? '');
  const open = po.lines.filter((l) => l.remainingQty > 0);
  const [rows, setRows] = useState(() => open.map((l) => ({
    lineId: l.id, qty: String(l.remainingQty), unitCostUsd: String(l.unitCostUsd),
  })));
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const setRow = (id, patch) => setRows((a) => a.map((r) => (r.lineId === id ? { ...r, ...patch } : r)));

  const submit = async () => {
    const lines = rows
      .filter((r) => Number(r.qty) > 0)
      .map((r) => ({ lineId: r.lineId, qty: Number(r.qty), unitCostUsd: Number(r.unitCostUsd) || 0 }));
    if (lines.length === 0) { setError(t('Kamida bitta qatorni qabul qiling')); return; }
    setBusy(true);
    try {
      await onSubmit({ receiptDate, invoiceNumber: invoiceNumber.trim() || null, lines });
      onClose();
    } catch (err) { setError(err.message); setBusy(false); }
  };

  return (
    <Modal title={t('Tovarni qabul qilish')} wide onClose={onClose}
      footer={<>
        <button className="btn btn-ghost" onClick={onClose} disabled={busy}>{t('Bekor qilish')}</button>
        <button className="btn btn-primary" onClick={submit} disabled={busy}>
          {busy ? t('Saqlanmoqda...') : t('Qabul qilish')}</button>
      </>}>
      <div className="form-row">
        <div className="field"><label>{t('Qabul sanasi')}</label>
          <input className="input" type="date" value={receiptDate} onChange={(e) => setReceiptDate(e.target.value)} /></div>
        <div className="field"><label>{t('Invoice raqami')}</label>
          <input className="input" value={invoiceNumber} onChange={(e) => setInvoiceNumber(e.target.value)} /></div>
      </div>
      {open.length === 0 ? (
        <EmptyState icon="✅" text={t('Hamma tovar qabul qilingan')} />
      ) : (
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr><th>{t('Mahsulot')}</th><th className="num">{t('Qoldiq')}</th>
              <th className="num">{t('Qabul')}</th><th className="num">{t('Kelish narxi')}</th></tr></thead>
            <tbody>
              {open.map((l) => {
                const r = rows.find((x) => x.lineId === l.id);
                return (
                  <tr key={l.id}>
                    <td className="name-cell">{l.productName}
                      <div className="faint" style={{ fontSize: 11 }}>
                        {t('buyurtma')} {l.orderedQty}, {t('kelgan')} {l.receivedQty}</div></td>
                    <td className="num">{l.remainingQty}</td>
                    <td><input className="input num" type="number" value={r?.qty ?? ''}
                               max={l.remainingQty}
                               onChange={(e) => setRow(l.id, { qty: e.target.value })} /></td>
                    <td><input className="input num" type="number" value={r?.unitCostUsd ?? ''}
                               onChange={(e) => setRow(l.id, { unitCostUsd: e.target.value })} /></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      <div className="faint" style={{ fontSize: 12, marginTop: 8 }}>
        {t('Qabul qilingan tovar tannarxi o‘rtacha-tortilgan (WAC) bo‘yicha yangilanadi.')}
      </div>
      {error && <div style={{ color: 'var(--red)', fontSize: 12, marginTop: 8 }}>{error}</div>}
    </Modal>
  );
}

function ValuationModal({ fmt, onClose }) {
  const t = useT();
  const { data, loading, error, reload } = useApi(() => CostingApi.valuation(), []);
  const [history, setHistory] = useState(null);

  const showHistory = async (productId) => {
    try { setHistory(await CostingApi.history(productId)); }
    catch { setHistory(null); }
  };

  return (
    <Modal title={t('Tannarx baholash (FIFO / WAC)')} wide onClose={onClose}>
      <Loader loading={loading} error={error} onRetry={reload}>
        {data && (
          <>
            <div className="flex-between" style={{ marginBottom: 10, flexWrap: 'wrap', gap: 8 }}>
              <span className="mono">{t('WAC jami')}: <b>{fmt(data.wacTotalUsd)}</b></span>
              <span className="mono">{t('FIFO jami')}: <b>{fmt(data.fifoTotalUsd)}</b></span>
            </div>
            {data.rows.length === 0 ? (
              <EmptyState icon="📦" text={t('Omborda tovar yo‘q')} />
            ) : (
              <div className="table-wrap">
                <table className="tbl">
                  <thead><tr><th>{t('Mahsulot')}</th><th className="num">{t('Qoldiq')}</th>
                    <th className="num">{t('WAC qiymat')}</th><th className="num">{t('FIFO qiymat')}</th><th /></tr></thead>
                  <tbody>
                    {data.rows.map((r) => (
                      <tr key={r.productId}>
                        <td className="name-cell">{r.productName}</td>
                        <td className="num">{r.qty}</td>
                        <td className="num mono">{fmt(r.wacValueUsd)}</td>
                        <td className="num mono">{fmt(r.fifoValueUsd)}</td>
                        <td className="right">
                          <button className="btn btn-ghost btn-sm" onClick={() => showHistory(r.productId)}>
                            📜 {t('Narx tarixi')}</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </Loader>

      {history && (
        <Modal title={`${t('Narx tarixi')}: ${history.productName}`} onClose={() => setHistory(null)}>
          <div className="faint" style={{ fontSize: 13, marginBottom: 8 }}>
            {t('Hozirgi o‘rtacha tannarx')}: <b>{fmt(history.currentWacUsd)}</b> · {t('qoldiq')} {history.currentQty}
          </div>
          {history.lots.length === 0 ? (
            <EmptyState icon="📜" text={t('Kelish tarixi yo‘q')} />
          ) : (
            <div className="table-wrap">
              <table className="tbl">
                <thead><tr><th>{t('Sana')}</th><th>{t('Yetkazib beruvchi')}</th>
                  <th className="num">{t('Miqdor')}</th><th className="num">{t('Narx')}</th><th>{t('Invoice')}</th></tr></thead>
                <tbody>
                  {history.lots.map((l) => (
                    <tr key={l.id}>
                      <td className="nowrap">{formatDate(l.receiptDate)}</td>
                      <td className="faint">{l.supplierName || '—'}</td>
                      <td className="num">{l.qty}</td>
                      <td className="num mono">{fmt(l.unitCostUsd)}</td>
                      <td className="faint">{l.invoiceNumber || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Modal>
      )}
    </Modal>
  );
}
