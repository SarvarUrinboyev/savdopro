import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ManagementApi, ReportApi } from '../api/endpoints.js';
import { downloadAuthed } from '../lib/download.js';
import { DateRangeFilter, rangeForPreset } from '../components/DateRangeFilter.jsx';
import { ConfirmDialog, Modal } from '../components/Modal.jsx';
import { useToast } from '../components/Toast.jsx';
import { TreasurySection } from '../components/TreasurySection.jsx';
import {
  CurrencyToggle, EmptyState, Loader, MetricCard, PageHeader,
} from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { useExchangeRate } from '../hooks/useExchangeRate.js';
import { useStickyState } from '../hooks/useStickyState.js';
import { convertMoney, formatDate, formatMoney, todayIso } from '../lib/format.js';
import { printSoldGoods } from '../lib/printSoldGoods.js';

const COST_TYPES = [
  ['SALARY', 'Ishchi oyligi'],
  ['TAX', 'Soliq'],
  ['OTHER', 'Boshqa xarajat'],
];
const COST_LABEL = { SALARY: 'Ishchi oyligi', TAX: 'Soliq', OTHER: 'Boshqa xarajat' };
const COST_BADGE = { SALARY: 'badge-aralash', TAX: 'badge-qarzga', OTHER: 'badge-muted' };

/**
 * Menejment page. Sales volume and gross profit come automatically from
 * warehouse SALE stock movements; salary / tax / other costs are entered
 * by hand. Every figure is stored in USD; the page toggle converts it.
 */
export function Management() {
  const t = useT();
  const [range, setRange] = useState({ preset: 'month', ...rangeForPreset('month') });
  const [displayCurrency, setDisplayCurrency] = useStickyState('barakat.cur.mgmt', 'UZS');
  const [modal, setModal] = useState(null);
  const toast = useToast();
  const rate = useExchangeRate();

  const { data, loading, error, reload } = useApi(
    () => ManagementApi.summary({ from: range.from, to: range.to }),
    [range.from, range.to],
  );

  const show = (usdValue) => convertMoney(usdValue, 'USD', displayCurrency, rate);

  const confirmDelete = async () => {
    try {
      await ManagementApi.removeCost(modal.item.id);
      toast.success(t("Xarajat o'chirildi"));
      setModal(null);
      reload();
    } catch (err) {
      toast.error(err.message);
    }
  };

  return (
    <>
      <PageHeader
        title={t('Moliya')}
        desc={t('Tanlangan davr bo‘yicha: foyda, xarajat va pul qoldig‘i (hisobot + eksport)')}
      >
        <Link to="/dashboard" className="btn btn-ghost"
              title={t('Bugungi jonli holat — kassa, savdo, buyurtmalar')}>
          ⚡ {t('Bugun')}
        </Link>
        <CurrencyToggle value={displayCurrency} onChange={setDisplayCurrency} />
        <button
          className="btn btn-ghost"
          title={t('Tanlangan davr uchun savdo hisobotini PDF qilib yuklab olish')}
          onClick={async () => {
            try {
              await downloadAuthed(
                ReportApi.salesPdfUrl({ from: range.from, to: range.to }),
                `savdo-${range.from}-${range.to}.pdf`,
              );
            } catch (err) { toast.error(err.message); }
          }}
        >
          📄 {t('PDF eksport')}
        </button>
        <button className="btn btn-primary" onClick={() => setModal({ type: 'add' })}>
          + {t("Xarajat qo'shish")}
        </button>
      </PageHeader>

      <DateRangeFilter value={range} onChange={setRange} />

      <SoldGoodsExportCard range={range} />

      <TreasurySection
        from={range.from} to={range.to}
        eyebrow="MAVJUD PUL MABLAG'LARI"
      />

      <Loader loading={loading} error={error} onRetry={reload}>
        {data && (
          <>
            {/* Headline KPIs only — the full breakdown (COGS, salaries, taxes,
                other) lives once in the "Foyda hisoboti" waterfall below, so we
                no longer repeat those figures as cards here. */}
            <div className="section-label">📊 {t('Asosiy ko‘rsatkichlar')}</div>
            <div className="metrics-4 section">
              <MetricCard tone="blue" icon="🛒" label={t('Savdo hajmi')}
                          value={show(data.salesRevenue)} currencyCode={displayCurrency} />
              <MetricCard tone="green" icon="📈" label={t('Yalpi foyda')}
                          value={show(data.grossProfit)} currencyCode={displayCurrency} />
              <MetricCard tone={Number(data.netProfit) >= 0 ? 'green' : 'red'} icon="💰"
                          label={t('Sof foyda')} value={show(data.netProfit)}
                          currencyCode={displayCurrency} />
              <MetricCard tone="blue" icon="📦" label={t('Sotilgan dona')}
                          value={data.unitsSold} currency={false} />
            </div>

            <div className="grid grid-2">
              <ProfitBreakdown data={data} show={show} cur={displayCurrency} />
              <CostsCard
                costs={data.costs}
                onAdd={() => setModal({ type: 'add' })}
                onEdit={(item) => setModal({ type: 'edit', item })}
                onDelete={(item) => setModal({ type: 'delete', item })}
              />
            </div>
          </>
        )}
      </Loader>

      {(modal?.type === 'add' || modal?.type === 'edit') && (
        <CostFormModal
          initial={modal.type === 'edit' ? modal.item : null}
          onSubmit={async (body) => {
            if (modal.type === 'add') {
              await ManagementApi.createCost(body);
              toast.success(t("Xarajat qo'shildi"));
            } else {
              await ManagementApi.updateCost(modal.item.id, body);
              toast.success(t('Xarajat yangilandi'));
            }
            reload();
          }}
          onClose={() => setModal(null)}
        />
      )}

      {modal?.type === 'delete' && (
        <ConfirmDialog
          title={t("Xarajatni o'chirish")}
          message={`"${modal.item.name}" ${t("xarajatini o'chirmoqchimisiz?")}`}
          confirmLabel={t("O'chirish")}
          onConfirm={confirmDelete}
          onCancel={() => setModal(null)}
        />
      )}
    </>
  );
}

/** Export bar — downloads the sold-goods list as CSV / Excel / PDF. */
function SoldGoodsExportCard({ range }) {
  const t = useT();
  const toast = useToast();
  const [pdfBusy, setPdfBusy] = useState(false);
  const [expBusy, setExpBusy] = useState(null);

  const params = { from: range.from, to: range.to };

  // Authenticated blob download — a plain <a href> drops the Bearer token and
  // the backend answers 401.
  const downloadExport = async (format) => {
    setExpBusy(format);
    try {
      await downloadAuthed(
        ManagementApi.soldGoodsExportUrl({ ...params, format }),
        `sotilgan-tovarlar-${range.from}_${range.to}.${format}`,
      );
    } catch (err) {
      toast.error(err.message);
    }
    setExpBusy(null);
  };

  const downloadPdf = async () => {
    setPdfBusy(true);
    try {
      const report = await ManagementApi.soldGoods(params);
      if (!report.lines || report.lines.length === 0) {
        toast.error(t("Bu davrda sotilgan tovar yo'q"));
      } else {
        printSoldGoods(report, t);
      }
    } catch (err) {
      toast.error(err.message);
    }
    setPdfBusy(false);
  };

  return (
    <div className="card card-pad section flex-between" style={{ flexWrap: 'wrap', gap: 12 }}>
      <div>
        <div style={{ fontWeight: 700 }}>📦 {t('Sotilgan tovarlar')}</div>
        <div className="faint" style={{ fontSize: 13 }}>
          {t("Tanlangan davr uchun sotilgan tovarlar ro'yxatini yuklab oling")}
        </div>
      </div>
      <div className="flex gap-8" style={{ flexWrap: 'wrap' }}>
        <button className="btn btn-ghost btn-sm" onClick={() => downloadExport('csv')}
                disabled={expBusy === 'csv'}>
          {expBusy === 'csv' ? t('Tayyorlanmoqda...') : '⬇ CSV'}
        </button>
        <button className="btn btn-ghost btn-sm" onClick={() => downloadExport('xlsx')}
                disabled={expBusy === 'xlsx'}>
          {expBusy === 'xlsx' ? t('Tayyorlanmoqda...') : '⬇ Excel'}
        </button>
        <button className="btn btn-ghost btn-sm" onClick={downloadPdf} disabled={pdfBusy}>
          {pdfBusy ? t('Tayyorlanmoqda...') : '⬇ PDF'}
        </button>
      </div>
    </div>
  );
}

function PnlRow({ label, value, cur, sign = '', strong, tone }) {
  return (
    <div
      className="flex-between"
      style={{ padding: '10px 0', borderBottom: '1px solid var(--border)' }}
    >
      <span style={{ fontWeight: strong ? 700 : 500 }}>{label}</span>
      <span
        className="mono"
        style={{
          fontWeight: strong ? 800 : 600,
          color: tone ? `var(--${tone})` : undefined,
        }}
      >
        {sign}{formatMoney(value, cur)}
      </span>
    </div>
  );
}

function ProfitBreakdown({ data, show, cur }) {
  const t = useT();
  const netPositive = Number(data.netProfit) >= 0;
  return (
    <div className="card">
      <div className="card-head"><h2>{t('Foyda hisoboti')}</h2></div>
      <div className="card-pad">
        <PnlRow label={t('Savdo hajmi')} value={show(data.salesRevenue)} cur={cur} />
        <PnlRow label={t('Tovar tannarxi')} value={show(data.costOfGoods)} cur={cur}
                sign="− " tone="red" />
        <PnlRow label={t('Yalpi foyda')} value={show(data.grossProfit)} cur={cur} strong tone="green" />
        <PnlRow label={t('Ishchi oyliklari')} value={show(data.salaryTotal)} cur={cur}
                sign="− " tone="red" />
        <PnlRow label={t('Soliqlar')} value={show(data.taxTotal)} cur={cur} sign="− " tone="red" />
        <PnlRow label={t('Boshqa xarajatlar')} value={show(data.otherTotal)} cur={cur}
                sign="− " tone="red" />
        <div className="flex-between" style={{ padding: '14px 0 2px' }}>
          <span style={{ fontWeight: 800, fontSize: 15 }}>{t('Sof foyda')}</span>
          <span
            className="mono"
            style={{
              fontWeight: 800, fontSize: 19,
              color: netPositive ? 'var(--green)' : 'var(--red)',
            }}
          >
            {formatMoney(show(data.netProfit), cur)}
          </span>
        </div>
      </div>
    </div>
  );
}

function CostsCard({ costs, onAdd, onEdit, onDelete }) {
  const t = useT();
  return (
    <div className="card">
      <div className="card-head">
        <h2>{t("Xarajatlar ro'yxati")}</h2>
        <button className="btn btn-primary btn-sm" onClick={onAdd}>+ {t("Qo'shish")}</button>
      </div>
      {costs.length === 0 ? (
        <EmptyState icon="🧾" text={t('Bu davrda xarajat kiritilmagan')} />
      ) : (
        <div className="table-wrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>{t('Sana')}</th>
                <th>{t('Turi')}</th>
                <th>{t('Nomi')}</th>
                <th className="num">{t('Summa')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {costs.map((c) => (
                <tr key={c.id}>
                  <td className="nowrap faint">{formatDate(c.date)}</td>
                  <td>
                    <span className={`badge ${COST_BADGE[c.type]}`}>
                      {t(COST_LABEL[c.type])}
                    </span>
                  </td>
                  <td className="name-cell">{c.name}</td>
                  <td className="num">{formatMoney(c.amount, c.currency)}</td>
                  <td>
                    <div className="row-actions">
                      <button className="icon-btn" title={t('Tahrirlash')}
                              onClick={() => onEdit(c)}>
                        ✏️
                      </button>
                      <button className="icon-btn danger" title={t("O'chirish")}
                              onClick={() => onDelete(c)}>
                        🗑
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function CostFormModal({ initial, onSubmit, onClose }) {
  const t = useT();
  const [type, setType] = useState(initial?.type ?? 'SALARY');
  const [name, setName] = useState(initial?.name ?? '');
  const [amount, setAmount] = useState(initial?.amount ?? '');
  const [currency, setCurrency] = useState(initial?.currency ?? 'UZS');
  const [date, setDate] = useState(initial?.date ?? todayIso());
  const [note, setNote] = useState(initial?.note ?? '');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const curLabel = currency === 'UZS' ? "so'm" : 'USD';

  const submit = async () => {
    if (!name.trim()) {
      setError(t('Nomi kiritilishi shart'));
      return;
    }
    if (!amount || Number(amount) <= 0) {
      setError(t("Summa musbat bo'lishi kerak"));
      return;
    }
    setBusy(true);
    try {
      await onSubmit({
        type,
        name: name.trim(),
        amount: Number(amount),
        currency,
        date,
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
      title={initial ? t('Xarajatni tahrirlash') : t('Yangi xarajat')}
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
      <div className="field">
        <label>{t('Xarajat turi')}</label>
        <div className="chip-row">
          {COST_TYPES.map(([key, label]) => (
            <button
              key={key}
              type="button"
              className={`chip ${type === key ? 'active' : ''}`}
              onClick={() => setType(key)}
            >
              {t(label)}
            </button>
          ))}
        </div>
      </div>
      <div className="field">
        <label>{type === 'SALARY' ? t('Ishchi ismi *') : t('Nomi *')}</label>
        <input className="input" autoFocus value={name}
               onChange={(e) => setName(e.target.value)}
               placeholder={type === 'SALARY' ? t('Masalan: Akmal') : t('Xarajat nomi')} />
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
      <div className="form-row">
        <div className="field">
          <label>{t('Summa')} ({curLabel})</label>
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
        <label>{t('Izoh (ixtiyoriy)')}</label>
        <input className="input" value={note} onChange={(e) => setNote(e.target.value)} />
      </div>
      {error && <div style={{ color: 'var(--red)', fontSize: 12 }}>{error}</div>}
    </Modal>
  );
}
