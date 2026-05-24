import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { CustomerApi, ProductApi, ReportApi } from '../api/endpoints.js';
import { downloadAuthed } from '../lib/download.js';
import { ConfirmDialog, Modal } from '../components/Modal.jsx';
import { NakladnoyPreview } from '../components/NakladnoyPreview.jsx';
import { useToast } from '../components/Toast.jsx';
import { EmptyState, Loader, MetricCard } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { formatDate, todayIso, usd } from '../lib/format.js';
import { balanceInfo, CustomerFormModal } from './Customers.jsx';

/** Customer detail: contact info, balance and the goods / payment ledger. */
export function CustomerDetail() {
  const { id } = useParams();
  const { data, loading, error, reload } = useApi(() => CustomerApi.detail(id), [id]);

  return (
    <Loader loading={loading} error={error} onRetry={reload}>
      {data && <Detail key={id} data={data} reload={reload} />}
    </Loader>
  );
}

function Detail({ data, reload }) {
  const navigate = useNavigate();
  const tr = useT();
  const toast = useToast();
  const [modal, setModal] = useState(null);
  const close = () => setModal(null);

  const customer = data.customer;
  const transactions = data.transactions;
  const info = balanceInfo(customer.balance);
  const balanceTone = info.tone === 'muted' ? 'blue' : info.tone;
  const goodsCount = transactions.filter((t) => t.type === 'GOODS').length;

  const removeCustomer = async () => {
    try {
      await CustomerApi.remove(customer.id);
      toast.success(tr("Mijoz o'chirildi"));
      navigate('/customers');
    } catch (err) {
      toast.error(err.message);
    }
  };

  const removeTransaction = async () => {
    try {
      await CustomerApi.removeTransaction(customer.id, modal.item.id);
      toast.success(tr("Amal o'chirildi"));
      close();
      reload();
    } catch (err) {
      toast.error(err.message);
    }
  };

  return (
    <>
      <div className="page-head">
        <div>
          <div style={{
            fontSize: 11, fontWeight: 700, letterSpacing: '.08em',
            color: 'var(--text-faint)',
          }}>
            <Link to="/customers" style={{ color: 'inherit' }}>{tr('MIJOZLAR')}</Link>
            {'  /  '}
            {customer.name.toUpperCase()}
          </div>
          <h1>{customer.name}</h1>
        </div>
        <div className="actions">
          <button className="btn btn-ghost" onClick={() => navigate('/customers')}>
            {tr('Orqaga')}
          </button>
          <button
            className="btn btn-ghost"
            title={tr("Mijoz tarixini PDF qilib yuklab olish")}
            onClick={async () => {
              try {
                await downloadAuthed(
                  ReportApi.customerLedgerPdfUrl(customer.id),
                  `${customer.name.replace(/\s+/g, '_')}-tarix.pdf`,
                );
              } catch (err) { window.alert(err.message); }
            }}
          >
            📄 {tr('PDF eksport')}
          </button>
          <button className="btn btn-ghost" onClick={() => setModal({ type: 'edit' })}>
            ✏️ {tr('Tahrirlash')}
          </button>
        </div>
      </div>

      <div className="metrics section">
        <MetricCard tone="amber" icon="📦" label={tr('Berilgan tovarlar')} value={customer.goodsTotal}
                    sub={`${goodsCount} ${tr('ta tovar')}`} />
        <MetricCard tone="green" icon="💵" label={tr("To'langan")} value={customer.paidTotal} />
        <MetricCard tone={balanceTone} icon={info.tone === 'green' ? '💚' : '📒'}
                    label={tr(info.label)} value={info.amount} />
      </div>

      {/* Phase 4.4 loyalty pill — only renders for customers who've
          ever earned a point so single-tx walk-ins stay uncluttered. */}
      {(customer.pointsBalance > 0 || customer.pointsTotalEarned > 0) && (
        <div className="card" style={{
          marginTop: -8, marginBottom: 16,
          background: 'linear-gradient(135deg, rgba(34,197,94,.08), rgba(59,130,246,.05))',
          borderColor: 'rgba(34,197,94,.25)',
        }}>
          <div className="flex-between" style={{ padding: '14px 18px' }}>
            <div>
              <div style={{ fontSize: 11, textTransform: 'uppercase',
                            letterSpacing: '.08em', color: 'var(--muted)' }}>
                ⭐ {tr('Loyalty ball')}
              </div>
              <div style={{ fontSize: 28, fontWeight: 800,
                            color: 'var(--brand-green, #22c55e)' }}>
                {Number(customer.pointsBalance).toLocaleString()}
              </div>
              <div style={{ fontSize: 11, color: 'var(--muted)' }}>
                {tr('Jami yutilgan')}: {Number(customer.pointsTotalEarned).toLocaleString()}
                {' · '}
                {tr('1 ball')} = 1 000 {tr("so'm chegirma")}
              </div>
            </div>
            <button
              className="btn btn-ghost"
              disabled={customer.pointsBalance <= 0}
              onClick={() => setModal({ type: 'redeem' })}
            >
              💰 {tr('Ball ishlatish')}
            </button>
          </div>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '1.6fr 1fr', gap: 16 }}>
        <div className="card">
          <div className="card-head">
            <h2>{tr('Amallar tarixi')}</h2>
            <div className="flex gap-8">
              <button
                className="btn btn-primary btn-sm"
                onClick={() => setModal({ type: 'tx', kind: 'GOODS' })}
              >
                📦 {tr('Tovar berish')}
              </button>
              <button
                className="btn btn-green btn-sm"
                onClick={() => setModal({ type: 'tx', kind: 'PAYMENT' })}
              >
                💵 {tr("To'lov olish")}
              </button>
            </div>
          </div>
          {transactions.length === 0 ? (
            <EmptyState icon="🧾" text={tr("Hali amal yo'q — tovar bering yoki to'lov qabul qiling")} />
          ) : (
            <DayGroupedLedger
              transactions={transactions}
              customer={customer}
              onEdit={(item) => setModal({ type: 'editTx', item })}
              onDelete={(item) => setModal({ type: 'deleteTx', item })}
            />
          )}
        </div>

        <div className="list-stack">
          <div className="card">
            <div className="card-head"><h2>{tr("Mijoz ma'lumotlari")}</h2></div>
            <div className="card-pad">
              <InfoRow label={tr('Ism')} value={customer.name} />
              <InfoRow label={tr('Telefon')} value={customer.phone || '—'} />
              <InfoRow label={tr('Manzil')} value={customer.address || '—'} />
              <InfoRow label={tr('Izoh')} value={customer.note || '—'} last />
            </div>
          </div>
          <button
            className="btn btn-ghost btn-sm"
            style={{ color: 'var(--red)' }}
            onClick={() => setModal({ type: 'deleteCustomer' })}
          >
            🗑 {tr("Mijozni o'chirish")}
          </button>
        </div>
      </div>

      {modal?.type === 'tx' && modal.kind === 'GOODS' && (
        <GiveGoodsModal
          customer={customer}
          onSubmit={async (items) => {
            await CustomerApi.addTransactions(customer.id, items);
            toast.success(tr('Tovarlar berildi'));
            reload();
          }}
          onPreview={(payload) => setModal({ type: 'preview', payload })}
          onClose={close}
        />
      )}

      {modal?.type === 'preview' && (
        <NakladnoyPreview
          {...modal.payload}
          onClose={close}
        />
      )}

      {modal?.type === 'tx' && modal.kind === 'PAYMENT' && (
        <ReceivePaymentModal
          onSubmit={async (body) => {
            await CustomerApi.addTransaction(customer.id, body);
            toast.success(tr("To'lov qabul qilindi"));
            reload();
          }}
          onClose={close}
        />
      )}

      {modal?.type === 'edit' && (
        <CustomerFormModal
          initial={customer}
          onSubmit={async (body) => {
            await CustomerApi.update(customer.id, body);
            toast.success(tr('Mijoz yangilandi'));
            reload();
          }}
          onClose={close}
        />
      )}

      {modal?.type === 'redeem' && (
        <RedeemPointsModal
          customer={customer}
          onClose={close}
          onDone={async (points) => {
            try {
              await CustomerApi.redeemPoints(customer.id, points);
              toast.success(`${tr('Ishlatildi')}: ${points} ${tr('ball')} (${points * 1000} ${tr("so'm chegirma")})`);
              close();
              reload();
            } catch (err) {
              toast.error(err.message);
            }
          }}
        />
      )}

      {modal?.type === 'editTx' && (
        <EditTxModal
          tx={modal.item}
          onSubmit={async (body) => {
            await CustomerApi.updateTransaction(customer.id, modal.item.id, body);
            toast.success(tr('Amal yangilandi'));
            reload();
          }}
          onClose={close}
        />
      )}

      {modal?.type === 'deleteTx' && (
        <ConfirmDialog
          title={tr("Amalni o'chirish")}
          message={tr("Bu amalni o'chirmoqchimisiz? Mijoz balansi qayta hisoblanadi.")}
          confirmLabel={tr("O'chirish")}
          onConfirm={removeTransaction}
          onCancel={close}
        />
      )}

      {modal?.type === 'deleteCustomer' && (
        <ConfirmDialog
          title={tr("Mijozni o'chirish")}
          message={`"${customer.name}" ${tr("mijozini va barcha amallarini o'chirmoqchimisiz?")}`}
          confirmLabel={tr("O'chirish")}
          onConfirm={removeCustomer}
          onCancel={close}
        />
      )}
    </>
  );
}

/**
 * Customer ledger grouped by date. Each day collapses all GOODS lines from
 * that day into a single expandable row with a print-nakladnoy button, so
 * a customer who bought 1,000 items still sees only one row per day.
 * Payments are shown separately, also grouped by day.
 */
function DayGroupedLedger({ transactions, customer, onEdit, onDelete }) {
  const t = useT();
  const [expanded, setExpanded] = useState(() => new Set());
  const [preview, setPreview] = useState(null);
  const toggle = (key) => setExpanded((prev) => {
    const next = new Set(prev);
    if (next.has(key)) next.delete(key); else next.add(key);
    return next;
  });

  const groups = useMemo(() => {
    const byDate = new Map();
    transactions.forEach((tx) => {
      if (!byDate.has(tx.date)) {
        byDate.set(tx.date, { date: tx.date, goods: [], payments: [] });
      }
      const g = byDate.get(tx.date);
      if (tx.type === 'GOODS') g.goods.push(tx);
      else g.payments.push(tx);
    });
    // Keep date-descending order: backend already returns newest first.
    return Array.from(byDate.values());
  }, [transactions]);

  const previewDay = (g) => {
    const paid = g.payments.reduce((s, p) => s + Number(p.amount || 0), 0);
    setPreview({
      customer,
      date: g.date,
      items: g.goods,
      paid,
    });
  };

  return (
    <div className="day-ledger">
      {preview && (
        <NakladnoyPreview
          {...preview}
          onClose={() => setPreview(null)}
        />
      )}
      {groups.map((g) => {
        const goodsKey = `g-${g.date}`;
        const payKey = `p-${g.date}`;
        const goodsTotal = g.goods.reduce((s, x) => s + Number(x.amount || 0), 0);
        const paidTotal = g.payments.reduce((s, x) => s + Number(x.amount || 0), 0);
        const goodsOpen = expanded.has(goodsKey);
        const payOpen = expanded.has(payKey);
        return (
          <div key={g.date} className="day-block">
            <div className="day-block-date">{formatDate(g.date)}</div>

            {g.goods.length > 0 && (
              <div className={`day-row goods ${goodsOpen ? 'open' : ''}`}>
                <div className="day-row-bar">
                  <button
                    type="button"
                    className="day-row-head"
                    onClick={() => toggle(goodsKey)}
                    aria-expanded={goodsOpen}
                  >
                    <span className="day-chev">{goodsOpen ? '▾' : '▸'}</span>
                    <span className="badge badge-karta">📦 {t('Tovar berildi')}</span>
                    <span className="day-row-summary">
                      {g.goods.length} {t('ta tovar')}
                    </span>
                    <span className="day-row-sum amount-neg mono">
                      +{usd(goodsTotal)}
                    </span>
                  </button>
                  <button
                    className="btn-debt outline day-print"
                    type="button"
                    onClick={() => previewDay(g)}
                    title={t('Nakladnoyni ko\'rish va chop etish')}
                  >
                    🖨 {t('Chop etish')}
                  </button>
                </div>
                {goodsOpen && (
                  <div className="day-row-body">
                    <table className="tbl day-items">
                      <thead>
                        <tr>
                          <th>{t('Tovar nomi')}</th>
                          <th className="num">{t('Narxi')}</th>
                          <th />
                        </tr>
                      </thead>
                      <tbody>
                        {g.goods.map((x) => (
                          <tr key={x.id}>
                            <td className="name-cell">{x.description || x.note || '—'}</td>
                            <td className="num amount-neg mono">+{usd(x.amount)}</td>
                            <td>
                              <div className="row-actions">
                                <button className="icon-btn" title={t('Tahrirlash')}
                                        onClick={() => onEdit(x)}>✏️</button>
                                <button className="icon-btn danger" title={t("O'chirish")}
                                        onClick={() => onDelete(x)}>🗑</button>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}

            {g.payments.length > 0 && (
              <div className={`day-row payments ${payOpen ? 'open' : ''}`}>
                <div className="day-row-bar">
                  <button
                    type="button"
                    className="day-row-head"
                    onClick={() => toggle(payKey)}
                    aria-expanded={payOpen}
                  >
                    <span className="day-chev">{payOpen ? '▾' : '▸'}</span>
                    <span className="badge badge-naqd">💵 {t("To'lov qabul qilindi")}</span>
                    <span className="day-row-summary">
                      {g.payments.length} {t('ta')}
                    </span>
                    <span className="day-row-sum amount-pos mono">
                      −{usd(paidTotal)}
                    </span>
                  </button>
                </div>
                {payOpen && (
                  <div className="day-row-body">
                    <table className="tbl day-items">
                      <thead>
                        <tr>
                          <th>{t('Tavsif')}</th>
                          <th className="num">{t('Summa')}</th>
                          <th />
                        </tr>
                      </thead>
                      <tbody>
                        {g.payments.map((x) => (
                          <tr key={x.id}>
                            <td className="name-cell">{x.description || x.note || '—'}</td>
                            <td className="num amount-pos mono">−{usd(x.amount)}</td>
                            <td>
                              <div className="row-actions">
                                <button className="icon-btn" title={t('Tahrirlash')}
                                        onClick={() => onEdit(x)}>✏️</button>
                                <button className="icon-btn danger" title={t("O'chirish")}
                                        onClick={() => onDelete(x)}>🗑</button>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

function InfoRow({ label, value, last }) {
  return (
    <div style={{
      display: 'flex', justifyContent: 'space-between', gap: 12,
      padding: '9px 0', borderBottom: last ? 'none' : '1px solid var(--border)',
    }}>
      <span className="faint" style={{ fontSize: 13 }}>{label}</span>
      <span style={{ fontWeight: 600, fontSize: 13, textAlign: 'right' }}>{value}</span>
    </div>
  );
}

/**
 * Sells one or more in-stock products to the customer. The product list
 * is a multi-select grid: tick the products and set each quantity.
 */
function GiveGoodsModal({ customer, onSubmit, onPreview, onClose }) {
  const t = useT();
  const { data: products, loading } = useApi(() => ProductApi.list(), []);
  const [search, setSearch] = useState('');
  const [selection, setSelection] = useState({});
  const [date, setDate] = useState(todayIso());
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const inStock = useMemo(
    () => (products || []).filter((p) => p.quantity > 0),
    [products],
  );
  const matches = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) {
      return inStock;
    }
    return inStock.filter((p) => p.name.toLowerCase().includes(q)
      || (p.barcode && p.barcode.toLowerCase().includes(q))
      || (p.imei1 && p.imei1.toLowerCase().includes(q))
      || (p.imei2 && p.imei2.toLowerCase().includes(q)));
  }, [inStock, search]);

  const toggle = (p) => {
    setSelection((cur) => {
      const next = { ...cur };
      if (p.id in next) {
        delete next[p.id];
      } else {
        next[p.id] = 1;
      }
      return next;
    });
    setError('');
  };

  // While typing we keep the raw field text so the cashier can enter any
  // number freely; it is clamped to [1, stock] only when the field blurs.
  const setQtyRaw = (p, raw) => {
    setSelection((cur) => ({ ...cur, [p.id]: raw }));
  };

  const clampQty = (p) => {
    setSelection((cur) => {
      if (!(p.id in cur)) {
        return cur;
      }
      const n = parseInt(cur[p.id], 10);
      const clamped = Math.max(1, Math.min(p.quantity, Number.isNaN(n) ? 1 : n));
      return { ...cur, [p.id]: clamped };
    });
  };

  // Effective integer quantity for totals / submit, ignoring in-progress text.
  const qtyOf = (p) => {
    const n = parseInt(selection[p.id], 10);
    return Number.isNaN(n) ? 1 : Math.max(1, Math.min(p.quantity, n));
  };

  const scan = () => {
    const code = search.trim();
    if (!code) {
      return;
    }
    const exact = (products || []).find((p) =>
      [p.barcode, p.imei1, p.imei2].some((v) => v && v === code));
    if (exact) {
      if (exact.quantity <= 0) {
        setError(`«${exact.name}» — ${t('bu tovar qolmagan')}`);
        return;
      }
      setSelection((cur) => {
        const prev = parseInt(cur[exact.id], 10) || 0;
        return { ...cur, [exact.id]: Math.min(exact.quantity, prev + 1) };
      });
      setSearch('');
      setError('');
    } else if (matches.length === 0) {
      setError(t('Bu tovar omborda topilmadi'));
    }
  };

  const chosen = inStock.filter((p) => p.id in selection);
  const total = chosen.reduce(
    (sum, p) => sum + Number(p.salePrice) * qtyOf(p), 0,
  );

  const submit = async (alsoPrint = false) => {
    if (chosen.length === 0) {
      setError(t('Kamida bitta tovar belgilang'));
      return;
    }
    setBusy(true);
    try {
      await onSubmit(chosen.map((p) => ({
        type: 'GOODS',
        productId: p.id,
        quantity: qtyOf(p),
        amount: Number(p.salePrice) * qtyOf(p),
        date,
        note: note.trim() || null,
      })));
      // After save, optionally show the nakladnoy preview so the user can
      // verify on screen before sending it to the printer.
      if (alsoPrint && customer && onPreview) {
        onPreview({
          customer,
          date,
          items: chosen.map((p) => ({
            description: `${p.name} × ${qtyOf(p)} @ ${usd(p.salePrice)}`,
            amount: Number(p.salePrice) * qtyOf(p),
          })),
          paid: 0, // on-credit by default
          note: note.trim() || null,
        });
        return;  // leave the give-goods modal closed via parent
      }
      onClose();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <Modal
      title={t('Tovar berish')}
      wide
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={busy}>
            {t('Bekor qilish')}
          </button>
          <button
            className="btn btn-ghost"
            onClick={() => submit(true)}
            disabled={busy || chosen.length === 0}
            title={t('Saqlab nakladnoy chop etish')}
          >
            🖨 {t('Saqlash + chop etish')}
          </button>
          <button className="btn btn-primary" onClick={() => submit(false)}
                  disabled={busy || chosen.length === 0}>
            {busy ? t('Saqlanmoqda...')
              : `${t('Saqlash')}${chosen.length
                ? ` · ${chosen.length} ${t('ta')} · ${usd(total)}` : ''}`}
          </button>
        </>
      }
    >
      <p className="muted" style={{ marginBottom: 10 }}>
        {t('Kerakli tovarlarni belgilang va sonini tanlang. Tovarlar ombordan ayiriladi.')}
      </p>
      <div className="field">
        <input
          className="input"
          autoFocus
          value={search}
          onChange={(e) => { setSearch(e.target.value); setError(''); }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              scan();
            }
          }}
          placeholder={t('Skanerlang yoki nomini yozing...')}
        />
      </div>
      <div className="tx-list" style={{ maxHeight: 300 }}>
        {loading && (
          <div className="faint" style={{ padding: 10, fontSize: 13 }}>...</div>
        )}
        {!loading && matches.length === 0 && (
          <div className="faint" style={{ padding: 10, fontSize: 13 }}>
            {t('Mavjud tovar topilmadi')}
          </div>
        )}
        {matches.map((p) => {
          const checked = p.id in selection;
          return (
            <div
              key={p.id}
              className={`goods-row ${checked ? 'checked' : ''}`}
              onClick={() => toggle(p)}
            >
              <input type="checkbox" checked={checked} readOnly />
              <span className="name-cell">{p.name}</span>
              <span className="faint" style={{ fontSize: 12 }}>
                {t('Qoldiq')}: {p.quantity}
              </span>
              <span className="mono" style={{ minWidth: 64, textAlign: 'right' }}>
                {usd(p.salePrice)}
              </span>
              <input
                className="input qty-input"
                type="number"
                min="1"
                max={p.quantity}
                disabled={!checked}
                value={checked ? selection[p.id] : ''}
                onChange={(e) => setQtyRaw(p, e.target.value)}
                onBlur={() => clampQty(p)}
                onClick={(e) => e.stopPropagation()}
              />
            </div>
          );
        })}
      </div>
      <div className="form-row" style={{ marginTop: 12 }}>
        <div className="field">
          <label>{t('Sana')}</label>
          <input className="input" type="date" value={date}
                 onChange={(e) => setDate(e.target.value)} />
        </div>
        <div className="field">
          <label>{t('Eslatma (ixtiyoriy)')}</label>
          <input className="input" value={note}
                 onChange={(e) => setNote(e.target.value)} />
        </div>
      </div>
      {error && <div style={{ color: 'var(--red)', fontSize: 12 }}>{error}</div>}
    </Modal>
  );
}

/** Records a payment received from the customer. */
function ReceivePaymentModal({ onSubmit, onClose }) {
  const t = useT();
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(todayIso());
  const [description, setDescription] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    if (!amount || Number(amount) <= 0) {
      setError(t("Summa musbat bo'lishi kerak"));
      return;
    }
    setBusy(true);
    try {
      await onSubmit({
        type: 'PAYMENT',
        date,
        description: description.trim() || null,
        amount: Number(amount),
        note: null,
      });
      onClose();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <Modal
      title={t("To'lov qabul qilish")}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={busy}>
            {t('Bekor qilish')}
          </button>
          <button className="btn btn-green" onClick={submit} disabled={busy}>
            {busy ? t('Saqlanmoqda...') : t('Saqlash')}
          </button>
        </>
      }
    >
      <p className="muted" style={{ marginBottom: 12 }}>
        {t("Qabul qilingan to'lov mijoz qarzini kamaytiradi.")}
      </p>
      <div className="form-row">
        <div className="field">
          <label>{t('Summa (USD)')}</label>
          <input className="input" type="number" autoFocus value={amount}
                 onChange={(e) => setAmount(e.target.value)} placeholder="0" />
        </div>
        <div className="field">
          <label>{t('Sana')}</label>
          <input className="input" type="date" value={date}
                 onChange={(e) => setDate(e.target.value)} />
        </div>
      </div>
      <div className="field">
        <label>{t('Izoh (ixtiyoriy)')}</label>
        <input className="input" value={description}
               onChange={(e) => setDescription(e.target.value)}
               placeholder={t("Masalan: naqd to'lov")} />
      </div>
      {error && <div style={{ color: 'var(--red)', fontSize: 12 }}>{error}</div>}
    </Modal>
  );
}

/** Edits an existing ledger line - corrects the amount, description or date. */
function EditTxModal({ tx, onSubmit, onClose }) {
  const t = useT();
  const isGoods = tx.type === 'GOODS';
  const [description, setDescription] = useState(tx.description ?? '');
  const [amount, setAmount] = useState(tx.amount ?? '');
  const [date, setDate] = useState(tx.date ?? todayIso());
  const [note, setNote] = useState(tx.note ?? '');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    if (!amount || Number(amount) <= 0) {
      setError(t("Summa musbat bo'lishi kerak"));
      return;
    }
    setBusy(true);
    try {
      await onSubmit({
        type: tx.type,
        date,
        description: description.trim() || null,
        amount: Number(amount),
        note: note.trim() || null,
      });
      onClose();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <Modal
      title={t('Amalni tahrirlash')}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={busy}>
            {t('Bekor qilish')}
          </button>
          <button className="btn btn-primary" onClick={submit} disabled={busy}>
            {busy ? t('Saqlanmoqda...') : t('Saqlash')}
          </button>
        </>
      }
    >
      <p className="muted" style={{ marginBottom: 12 }}>
        {t("Yozuvni to'g'rilash — ombor qoldig'iga ta'sir qilmaydi.")}
      </p>
      <div className="field">
        <label>{isGoods ? t('Tovar nomi') : t('Izoh')}</label>
        <input className="input" autoFocus value={description}
               onChange={(e) => setDescription(e.target.value)} />
      </div>
      <div className="form-row">
        <div className="field">
          <label>{t('Summa (USD)')}</label>
          <input className="input" type="number" value={amount}
                 onChange={(e) => setAmount(e.target.value)} placeholder="0" />
        </div>
        <div className="field">
          <label>{t('Sana')}</label>
          <input className="input" type="date" value={date}
                 onChange={(e) => setDate(e.target.value)} />
        </div>
      </div>
      <div className="field">
        <label>{t('Eslatma (ixtiyoriy)')}</label>
        <input className="input" value={note}
               onChange={(e) => setNote(e.target.value)} />
      </div>
      {error && <div style={{ color: 'var(--red)', fontSize: 12 }}>{error}</div>}
    </Modal>
  );
}

/**
 * Burn loyalty points for a UZS-equivalent discount (Phase 4.4).
 * Caller updates the backend via CustomerApi.redeemPoints and re-fetches
 * the customer; this modal only collects the amount.
 */
function RedeemPointsModal({ customer, onClose, onDone }) {
  const t = useT();
  const [points, setPoints] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const max = customer.pointsBalance || 0;
  const num = Number(points);
  const valid = Number.isFinite(num) && num > 0 && num <= max;

  const submit = async () => {
    setError('');
    if (!valid) {
      setError(`${t('1 dan')} ${max} ${t('gacha kiriting')}`);
      return;
    }
    setBusy(true);
    try {
      await onDone(num);
    } catch (e) {
      setError(e.message);
      setBusy(false);
    }
  };

  const quick = (frac) => setPoints(String(Math.floor(max * frac)));

  return (
    <Modal
      title={t('Ball ishlatish')}
      onClose={onClose}
      footer={(
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={busy}>
            {t('Bekor qilish')}
          </button>
          <button className="btn btn-primary" onClick={submit} disabled={busy || !valid}>
            {busy ? t('Ishlanmoqda...')
                  : `${num || 0} ${t('ball')} → ${(num || 0) * 1000} ${t("so'm")}`}
          </button>
        </>
      )}
    >
      <p className="muted" style={{ marginTop: 0, fontSize: 13 }}>
        {t('Mavjud')}: <b>{max.toLocaleString()}</b> {t('ball')}
        {' · '} 1 {t('ball')} = 1 000 {t("so'm chegirma")}
      </p>
      <div className="field">
        <label>{t('Necha ball ishlatamiz?')}</label>
        <input
          className="input" type="number" min="1" max={max}
          value={points} onChange={(e) => setPoints(e.target.value)}
          placeholder="0" autoFocus
        />
      </div>
      <div className="flex gap-8" style={{ marginTop: 6 }}>
        <button className="btn btn-ghost btn-sm" type="button"
                onClick={() => quick(0.25)}>25%</button>
        <button className="btn btn-ghost btn-sm" type="button"
                onClick={() => quick(0.5)}>50%</button>
        <button className="btn btn-ghost btn-sm" type="button"
                onClick={() => quick(0.75)}>75%</button>
        <button className="btn btn-ghost btn-sm" type="button"
                onClick={() => setPoints(String(max))}>100%</button>
      </div>
      {error && (
        <div style={{ color: 'var(--red)', fontSize: 12, marginTop: 8 }}>
          ⚠ {error}
        </div>
      )}
    </Modal>
  );
}
