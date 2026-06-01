import { useState } from 'react';
import { ReportApi, ShiftApi } from '../api/endpoints.js';
import { useT } from '../context/Settings.jsx';
import { Receipt } from '../components/Receipt.jsx';
import { useToast } from '../components/Toast.jsx';
import { EmptyState, Loader, PageHeader } from '../components/ui.jsx';
import { useApi } from '../hooks/useApi.js';
import { formatDate, money, usd } from '../lib/format.js';

export function ShiftClose({ onClosed }) {
  const { data: report, loading, error, reload } = useApi(() => ReportApi.endOfDay(), []);
  const [busy, setBusy] = useState(false);
  const toast = useToast();
  const t = useT();

  const sendTelegram = async () => {
    setBusy(true);
    try {
      await ReportApi.sendTelegram();
      toast.success(t('Hisobot Telegramga yuborildi'));
    } catch (err) {
      toast.error(err.message);
    }
    setBusy(false);
  };

  const closeShift = async () => {
    setBusy(true);
    try {
      await ShiftApi.close();
      toast.success(t('Smena yopildi va hisobot yuborildi'));
      window.print();
      onClosed();
    } catch (err) {
      toast.error(err.message);
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader title={t('Smena yopish')} desc={t('Kunlik hisobot va smenani yakunlash')} />
      <Loader loading={loading} error={error} onRetry={reload}>
        {report && (
          <ReportView
            report={report}
            busy={busy}
            onSend={sendTelegram}
            onPrint={() => window.print()}
            onClose={closeShift}
          />
        )}
      </Loader>
    </>
  );
}

function Row({ label, value, strong, tone }) {
  return (
    <div className="flex-between" style={{ padding: '6px 0' }}>
      <span className="muted">{label}</span>
      <span
        className="mono"
        style={{ fontWeight: strong ? 800 : 600, color: tone ? `var(--${tone})` : undefined }}
      >
        {usd(value)}
      </span>
    </div>
  );
}

function ReportView({ report, busy, onSend, onPrint, onClose }) {
  const t = useT();
  const s = report.sales;
  return (
    <div className="grid grid-2">
      <div>
        {s && (
          <div className="card section">
            <div className="card-head">
              <h2>🛒 {t('Savdo (bugun)')}</h2>
            </div>
            <div className="card-pad">
              <div className="flex-between" style={{ padding: '6px 0' }}>
                <span className="muted">{t('Cheklar')}</span>
                <span className="mono" style={{ fontWeight: 600 }}>{s.count} {t('ta')}</span>
              </div>
              <Row label={t('Sof savdo')} value={s.net} />
              {Number(s.refunded) > 0 && (
                <Row label={t('Qaytarilgan')} value={s.refunded} tone="red" />
              )}
              <Row label={t('Tannarx (taxminiy)')} value={s.cogs} />
              <hr style={{ border: 0, borderTop: '1px solid var(--border)', margin: '4px 0' }} />
              <Row label={t('Sof foyda (taxminiy)')} value={s.profit} strong tone="green" />
            </div>
          </div>
        )}
        <div className="card section">
          <div className="card-head">
            <h2>🛒 {t('Supermarket xarajati')}</h2>
          </div>
          <div className="card-pad">
            <Row label={t('Kassadan')} value={report.marketKassa} />
            <Row label={t('Naqddan')} value={report.marketNaqd} />
            <Row label={t('Kartadan')} value={report.marketKarta} />
            <hr style={{ border: 0, borderTop: '1px solid var(--border)', margin: '4px 0' }} />
            <Row label={t('Jami')} value={report.marketTotal} strong />
          </div>
        </div>

        <div className="card section">
          <div className="card-head">
            <h2>🏠 {t('Uy xarajatlari')}</h2>
          </div>
          <div className="card-pad">
            <Row label={t('Naqddan')} value={report.homeNaqd} />
            <Row label={t('Kassadan')} value={report.homeKassa} />
            <Row label={t('Kartadan')} value={report.homeKarta} />
            <hr style={{ border: 0, borderTop: '1px solid var(--border)', margin: '4px 0' }} />
            <Row label={t('Jami')} value={report.homeTotal} strong />
          </div>
        </div>

        <div className="card section">
          <div className="card-head">
            <h2>💵 {t('Kassa holati')}</h2>
          </div>
          <div className="card-pad">
            <Row label={t('Ertalabgi balans')} value={report.startingCash} />
            <Row label={t('Chiqdi (naqd)')} value={report.cashOut} tone="red" />
            <hr style={{ border: 0, borderTop: '1px solid var(--border)', margin: '4px 0' }} />
            <Row label={t('Taxminiy qoldiq')} value={report.estimatedCash} strong tone="green" />
          </div>
        </div>

        <div className="card section">
          <div className="card-head">
            <h2>📒 {t('Qarzlar')}</h2>
          </div>
          <div className="card-pad">
            <Row label={t('Mening qarzlarim')} value={report.myDebtTotal} tone="red" />
            <Row label={t('Bizdan qarzlar')} value={report.customerDebtTotal} tone="green" />
          </div>
        </div>

        <div className="card">
          <div className="card-head">
            <h2>📦 {t('Ertaga keladi / Kelmagan')}</h2>
          </div>
          <div className="card-pad">
            <div className="section-label">
              <span className="tag tag-amber" /> {t('Ertaga keladi')}
            </div>
            {report.tomorrowOrders.length === 0 ? (
              <div className="faint" style={{ fontSize: 13 }}>{t("Buyurtma yo'q")}</div>
            ) : (
              <>
                {report.tomorrowOrders.map((o) => (
                  <div key={o.id} className="flex-between" style={{ fontSize: 13, padding: '3px 0' }}>
                    <span>{o.name}</span>
                    <span className="mono">{money(o.amount)}</span>
                  </div>
                ))}
                <Row label={t('Jami')} value={report.tomorrowOrdersTotal} strong />
              </>
            )}
            <div className="section-label mt-16">
              <span className="tag tag-red" /> {t("Kelmagan (muddati o'tgan)")}
            </div>
            {report.overdueOrders.length === 0 ? (
              <div className="faint" style={{ fontSize: 13 }}>{t("Yo'q")}</div>
            ) : (
              report.overdueOrders.map((o) => (
                <div key={o.id} className="flex-between" style={{ fontSize: 13, padding: '3px 0' }}>
                  <span>{o.name}</span>
                  <span className="faint">{formatDate(o.deliveryDate)}</span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      <div>
        <div className="card section">
          <div className="card-head">
            <h2>🧾 {t('Chek (XP-80C)')}</h2>
            <span className="hint">80mm</span>
          </div>
          <div className="card-pad" style={{ display: 'grid', placeItems: 'center' }}>
            <Receipt text={report.receiptText} />
          </div>
        </div>

        <div className="card">
          <div className="card-head">
            <h2>{t('Amallar')}</h2>
          </div>
          <div className="card-pad list-stack">
            <button className="btn btn-accent btn-block" onClick={onSend} disabled={busy}>
              ✈️ {t('Telegram ga yuborish')}
            </button>
            <button className="btn btn-ghost btn-block" onClick={onPrint} disabled={busy}>
              🖨 {t('Faqat chek chiqarish')}
            </button>
            <button className="btn btn-red btn-block" onClick={onClose} disabled={busy}>
              🔒 {t('Smenani yopish')}
            </button>
            <p className="faint" style={{ fontSize: 12, textAlign: 'center' }}>
              {t('Smenani yopish: chek chiqadi, Telegramga yuboriladi va smena yakunlanadi.')}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
