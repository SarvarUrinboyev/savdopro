import { useMemo, useState } from 'react';
import { AccountingApi } from '../api/endpoints.js';
import { AccountingTabs } from '../components/AccountingTabs.jsx';
import { DateRangeFilter, rangeForPreset } from '../components/DateRangeFilter.jsx';
import { ConfirmDialog, Modal } from '../components/Modal.jsx';
import { useToast } from '../components/Toast.jsx';
import { CurrencyToggle, EmptyState, Loader, PageHeader } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { useExchangeRate } from '../hooks/useExchangeRate.js';
import { useStickyState } from '../hooks/useStickyState.js';
import { convertMoney, formatDate, formatMoney, todayIso } from '../lib/format.js';

const SOURCE_LABEL = {
  MANUAL: "Qo'lda", SALE: 'Sotuv', SALE_REFUND: 'Qaytarish', STOCK_IN: 'Tovar kirim',
  STOCK_WRITEOFF: 'Hisobdan chiqarish', EXPENSE: 'Xarajat', HOME_EXPENSE: "Do'kon xarajati",
  MANAGEMENT_COST: 'Boshqaruv', PAYMENT: "To'lov", OPENING_BALANCE: "Boshlang'ich",
};

/** Journal entries ("Jurnal yozuvlari") — the raw double-entry feed. */
export function JournalEntries() {
  const t = useT();
  const toast = useToast();
  const rate = useExchangeRate();
  const [range, setRange] = useState({ preset: 'month', ...rangeForPreset('month') });
  const [cur, setCur] = useStickyState('barakat.cur.acct', 'USD');
  const [expanded, setExpanded] = useState(() => new Set());
  const [modal, setModal] = useState(null);

  const { data, loading, error, reload } = useApi(
    () => AccountingApi.journal({ from: range.from, to: range.to }),
    [range.from, range.to],
  );
  const accountsApi = useApi(() => AccountingApi.accounts(), []);

  const fmt = (u) => formatMoney(convertMoney(u, 'USD', cur, rate), cur);
  const toggle = (id) => setExpanded((s) => {
    const n = new Set(s);
    if (n.has(id)) n.delete(id); else n.add(id);
    return n;
  });

  return (
    <>
      <PageHeader title={t('Jurnal yozuvlari')}
                  desc={t('Ikki tomonlama yozuvlar — avtomatik va qo‘lda')}>
        <CurrencyToggle value={cur} onChange={setCur} />
        <button className="btn btn-primary" onClick={() => setModal({ type: 'add' })}>
          + {t('Yangi yozuv')}
        </button>
      </PageHeader>

      <AccountingTabs />
      <DateRangeFilter value={range} onChange={setRange} />

      <div className="card">
        <div className="card-head">
          <h2>{t('Yozuvlar')}</h2>
          {data && <span className="hint">
            {t('Debet')}: {fmt(data.totalDebit)} · {t('Kredit')}: {fmt(data.totalCredit)}
          </span>}
        </div>
        <Loader loading={loading} error={error} onRetry={reload}>
          {!data || data.entries.length === 0 ? (
            <EmptyState icon="📒" text={t('Bu davrda yozuv yo‘q')} />
          ) : (
            <div className="table-wrap">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>{t('Sana')}</th>
                    <th>{t('Izoh')}</th>
                    <th>{t('Manba')}</th>
                    <th className="num">{t('Debet')}</th>
                    <th className="num">{t('Kredit')}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.entries.map((e) => (
                    <RowGroup key={e.id} entry={e} expanded={expanded.has(e.id)}
                              onToggle={() => toggle(e.id)} fmt={fmt}
                              onReverse={() => setModal({ type: 'reverse', item: e })}
                              onDelete={() => setModal({ type: 'delete', item: e })} />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Loader>
      </div>

      {modal?.type === 'add' && (
        <ManualEntryModal
          accounts={accountsApi.data || []}
          onSubmit={async (body) => {
            await AccountingApi.createJournal(body);
            toast.success(t('Yozuv qo‘shildi'));
            reload();
          }}
          onClose={() => setModal(null)}
        />
      )}

      {modal?.type === 'reverse' && (
        <ConfirmDialog
          title={t('Storno (teskari yozuv)')}
          message={t('Bu yozumni teskari yozuv bilan bekor qilmoqchimisiz?')}
          confirmLabel={t('Storno qilish')}
          onConfirm={async () => {
            try {
              await AccountingApi.reverseJournal(modal.item.id);
              toast.success(t('Storno yozuvi yaratildi'));
              setModal(null); reload();
            } catch (err) { toast.error(err.message); }
          }}
          onCancel={() => setModal(null)}
        />
      )}

      {modal?.type === 'delete' && (
        <ConfirmDialog
          title={t("Yozuvni o'chirish")}
          message={t("Faqat qo'lda kiritilgan yozuv o'chiriladi. Davom etamizmi?")}
          confirmLabel={t("O'chirish")}
          onConfirm={async () => {
            try {
              await AccountingApi.removeJournal(modal.item.id);
              toast.success(t("Yozuv o'chirildi"));
              setModal(null); reload();
            } catch (err) { toast.error(err.message); }
          }}
          onCancel={() => setModal(null)}
        />
      )}
    </>
  );
}

function RowGroup({ entry, expanded, onToggle, fmt, onReverse, onDelete }) {
  const t = useT();
  const manual = entry.source === 'MANUAL';
  return (
    <>
      <tr style={{ cursor: 'pointer' }} onClick={onToggle}>
        <td className="nowrap faint">{formatDate(entry.entryDate)}</td>
        <td className="name-cell">{entry.memo || '—'}</td>
        <td><span className="badge badge-muted">{t(SOURCE_LABEL[entry.source] || entry.source)}</span></td>
        <td className="num mono">{fmt(entry.totalDebit)}</td>
        <td className="num mono">{fmt(entry.totalCredit)}</td>
        <td className="right" onClick={(ev) => ev.stopPropagation()}>
          <div className="row-actions">
            <button className="icon-btn" title={t('Storno')} onClick={onReverse}>↩️</button>
            {manual && <button className="icon-btn danger" title={t("O'chirish")}
                               onClick={onDelete}>🗑</button>}
          </div>
        </td>
      </tr>
      {expanded && entry.lines.map((l) => (
        <tr key={l.id} style={{ background: 'var(--surface-2, rgba(0,0,0,0.02))' }}>
          <td />
          <td colSpan={2} style={{ paddingLeft: 24, fontSize: 13 }}>
            <span className="mono faint">{l.accountCode}</span> · {l.accountName}
            {l.memo && <span className="faint"> — {l.memo}</span>}
          </td>
          <td className="num mono" style={{ fontSize: 13 }}>
            {Number(l.debit) > 0 ? fmt(l.debit) : ''}</td>
          <td className="num mono" style={{ fontSize: 13 }}>
            {Number(l.credit) > 0 ? fmt(l.credit) : ''}</td>
          <td />
        </tr>
      ))}
    </>
  );
}

const blankLine = () => ({ accountId: '', debit: '', credit: '' });

function ManualEntryModal({ accounts, onSubmit, onClose }) {
  const t = useT();
  const [entryDate, setEntryDate] = useState(todayIso());
  const [memo, setMemo] = useState('');
  const [currency, setCurrency] = useState('USD');
  const [lines, setLines] = useState(() => [blankLine(), blankLine()]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const totals = useMemo(() => {
    let d = 0; let c = 0;
    for (const l of lines) { d += Number(l.debit) || 0; c += Number(l.credit) || 0; }
    return { d, c, balanced: Math.abs(d - c) < 0.01 && d > 0 };
  }, [lines]);

  const setLine = (i, patch) => setLines((arr) =>
    arr.map((l, idx) => (idx === i ? { ...l, ...patch } : l)));
  const addLine = () => setLines((arr) => [...arr, blankLine()]);
  const removeLine = (i) => setLines((arr) => arr.length > 2 ? arr.filter((_, idx) => idx !== i) : arr);

  const submit = async () => {
    const filled = lines.filter((l) => l.accountId && (Number(l.debit) > 0 || Number(l.credit) > 0));
    if (filled.length < 2) { setError(t('Kamida 2 ta to‘ldirilgan qator kerak')); return; }
    if (!totals.balanced) { setError(t('Debet va kredit teng bo‘lishi kerak')); return; }
    setBusy(true);
    try {
      await onSubmit({
        entryDate,
        memo: memo.trim() || null,
        lines: filled.map((l) => ({
          accountId: Number(l.accountId),
          debit: Number(l.debit) || 0,
          credit: Number(l.credit) || 0,
          currency,
        })),
      });
      onClose();
    } catch (err) { setError(err.message); setBusy(false); }
  };

  return (
    <Modal
      title={t('Yangi jurnal yozuvi')}
      wide
      onClose={onClose}
      footer={
        <>
          <div className="mono" style={{ marginRight: 'auto', fontWeight: 700,
            color: totals.balanced ? 'var(--green)' : 'var(--red)' }}>
            Σ {t('Debet')} {totals.d.toFixed(2)} · {t('Kredit')} {totals.c.toFixed(2)}
          </div>
          <button className="btn btn-ghost" onClick={onClose} disabled={busy}>{t('Bekor qilish')}</button>
          <button className="btn btn-primary" onClick={submit} disabled={busy || !totals.balanced}>
            {busy ? t('Saqlanmoqda...') : t('Saqlash')}
          </button>
        </>
      }
    >
      <div className="form-row">
        <div className="field">
          <label>{t('Sana')}</label>
          <input className="input" type="date" value={entryDate}
                 onChange={(e) => setEntryDate(e.target.value)} />
        </div>
        <div className="field">
          <label>{t('Valyuta')}</label>
          <div className="chip-row">
            <button type="button" className={`chip ${currency === 'UZS' ? 'active' : ''}`}
                    onClick={() => setCurrency('UZS')}>{t("so'm")}</button>
            <button type="button" className={`chip ${currency === 'USD' ? 'active' : ''}`}
                    onClick={() => setCurrency('USD')}>USD</button>
          </div>
        </div>
      </div>
      <div className="field">
        <label>{t('Izoh')}</label>
        <input className="input" value={memo} onChange={(e) => setMemo(e.target.value)}
               placeholder={t('Yozuv izohi')} />
      </div>

      <div className="table-wrap" style={{ marginTop: 8 }}>
        <table className="tbl">
          <thead>
            <tr><th>{t('Hisob')}</th><th className="num">{t('Debet')}</th>
              <th className="num">{t('Kredit')}</th><th /></tr>
          </thead>
          <tbody>
            {lines.map((l, i) => (
              <tr key={i}>
                <td>
                  <select className="select" value={l.accountId}
                          onChange={(e) => setLine(i, { accountId: e.target.value })}>
                    <option value="">{t('Tanlang...')}</option>
                    {accounts.filter((a) => a.active).map((a) => (
                      <option key={a.id} value={a.id}>{a.code} · {a.name}</option>
                    ))}
                  </select>
                </td>
                <td>
                  <input className="input num" type="number" value={l.debit}
                         onChange={(e) => setLine(i, { debit: e.target.value, credit: '' })}
                         placeholder="0" />
                </td>
                <td>
                  <input className="input num" type="number" value={l.credit}
                         onChange={(e) => setLine(i, { credit: e.target.value, debit: '' })}
                         placeholder="0" />
                </td>
                <td>
                  <button className="icon-btn danger" title={t("O'chirish")}
                          onClick={() => removeLine(i)} disabled={lines.length <= 2}>✕</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <button className="btn btn-ghost btn-sm" onClick={addLine} style={{ marginTop: 8 }}>
        + {t('Qator qo‘shish')}
      </button>
      {error && <div style={{ color: 'var(--red)', fontSize: 12, marginTop: 8 }}>{error}</div>}
    </Modal>
  );
}
