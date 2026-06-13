import { useState } from 'react';
import { BillingApi, ReconciliationApi } from '../api/endpoints.js';
import { AccountingTabs } from '../components/AccountingTabs.jsx';
import { DateRangeFilter, rangeForPreset } from '../components/DateRangeFilter.jsx';
import { useToast } from '../components/Toast.jsx';
import { CurrencyToggle, EmptyState, Loader, MetricCard, PageHeader } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { useExchangeRate } from '../hooks/useExchangeRate.js';
import { useStickyState } from '../hooks/useStickyState.js';
import { convertMoney, formatDate, formatMoney, money } from '../lib/format.js';

const ONLINE_BADGE = {
  MATCHED: 'badge-naqd', PENDING: 'badge-aralash',
  UNRECONCILED: 'badge-qarzga', CANCELLED: 'badge-muted',
};
const ONLINE_LABEL = {
  MATCHED: 'Moslangan', PENDING: 'Kutilmoqda',
  UNRECONCILED: 'Yozilmagan', CANCELLED: 'Bekor qilingan',
};

/**
 * Bank/payments reconciliation: Click/Payme online payments matched to debt,
 * card-terminal settlement vs POS card sales, and the subscription status.
 */
export function Reconciliation() {
  const t = useT();
  const toast = useToast();
  const rate = useExchangeRate();
  const [range, setRange] = useState({ preset: 'month', ...rangeForPreset('month') });
  const [cur, setCur] = useStickyState('barakat.cur.acct', 'USD');
  const [crediting, setCrediting] = useState(null);

  const { data, loading, error, reload } = useApi(
    () => ReconciliationApi.get({ from: range.from, to: range.to }),
    [range.from, range.to],
  );
  const billing = useApi(() => BillingApi.status().catch(() => null), []);

  const usd = (u) => formatMoney(convertMoney(u, 'USD', cur, rate), cur);
  const som = (s) => money(s) + " so'm";

  const credit = async (id) => {
    setCrediting(id);
    try {
      const r = await ReconciliationApi.creditOnline(id);
      if (r.credited) toast.success(t('Qarzga yozildi'));
      else toast.info(r.message || t('O‘zgarish bo‘lmadi'));
      reload();
    } catch (err) {
      toast.error(err.message);
    }
    setCrediting(null);
  };

  return (
    <>
      <PageHeader title={t('Moslashtirish')}
                  desc={t('Click/Payme, bank va obuna tushumlarini ichki yozuvlar bilan solishtirish')}>
        <CurrencyToggle value={cur} onChange={setCur} />
      </PageHeader>

      <AccountingTabs />
      <DateRangeFilter value={range} onChange={setRange} />

      <SubscriptionCard billing={billing} />

      <Loader loading={loading} error={error} onRetry={reload}>
        {data && (
          <>
            {/* ---- Online (Click/Payme) ↔ debt ---- */}
            <div className="section-label">💳 {t('Onlayn to‘lovlar (Click/Payme) — qarz')}</div>
            <div className="metrics-4 section">
              <MetricCard tone="green" icon="✅" label={t('Moslangan')}
                          value={data.onlineSummary.matched} currency={false} />
              <MetricCard tone="amber" icon="⏳" label={t('Kutilmoqda')}
                          value={data.onlineSummary.pending} currency={false} />
              <MetricCard tone="red" icon="⚠️" label={t('Yozilmagan')}
                          value={data.onlineSummary.unreconciled} currency={false} />
              <MetricCard tone="blue" icon="💰" label={t('Moslangan summa')}
                          value={convertMoney(data.onlineSummary.matchedUsd, 'USD', cur, rate)}
                          currencyCode={cur} />
            </div>
            <div className="card">
              <div className="card-head"><h2>{t('Onlayn to‘lovlar')}</h2></div>
              {data.online.length === 0 ? (
                <EmptyState icon="💳" text={t('Bu davrda onlayn to‘lov yo‘q')} />
              ) : (
                <div className="table-wrap">
                  <table className="tbl">
                    <thead>
                      <tr>
                        <th>{t('Provayder')}</th>
                        <th>{t('Mijoz')}</th>
                        <th className="num">{t('Summa')}</th>
                        <th>{t('Holat')}</th>
                        <th />
                      </tr>
                    </thead>
                    <tbody>
                      {data.online.map((r) => (
                        <tr key={r.id}>
                          <td className="mono">{r.provider}</td>
                          <td className="name-cell">
                            {r.customerName || '—'}
                            {r.note && <div className="faint" style={{ fontSize: 11 }}>{r.note}</div>}
                          </td>
                          <td className="num">{usd(r.amountUsd)}</td>
                          <td>
                            <span className={`badge ${ONLINE_BADGE[r.status] || 'badge-muted'}`}>
                              {t(ONLINE_LABEL[r.status] || r.status)}
                            </span>
                          </td>
                          <td className="right">
                            {r.status === 'UNRECONCILED' && (
                              <button className="btn btn-primary btn-sm"
                                      disabled={crediting === r.id}
                                      onClick={() => credit(r.id)}>
                                {crediting === r.id ? t('...') : t('Qarzga yozish')}
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* ---- Terminal (bank) ↔ card sales ---- */}
            <div className="section-label">🏧 {t('Karta terminali — sotuv (so‘mda)')}</div>
            <div className="metrics-4 section">
              <MetricCard tone="blue" icon="🏦" label={t('Terminal jami')}
                          value={data.terminalSummary.terminalTotalSom} currencyCode="UZS" />
              <MetricCard tone="blue" icon="🛒" label={t('POS karta sotuv')}
                          value={data.terminalSummary.posCardTotalSom} currencyCode="UZS" />
              <MetricCard tone={Number(data.terminalSummary.diffSom) === 0 ? 'green' : 'amber'}
                          icon="➖" label={t('Farq')}
                          value={data.terminalSummary.diffSom} currencyCode="UZS" />
              <MetricCard tone={data.terminalSummary.discrepancyDays > 0 ? 'red' : 'green'}
                          icon="📅" label={t('Nomuvofiq kunlar')}
                          value={data.terminalSummary.discrepancyDays} currency={false} />
            </div>
            <div className="card">
              <div className="card-head"><h2>{t('Kunlik solishtirish')}</h2></div>
              {data.terminal.length === 0 ? (
                <EmptyState icon="🏧" text={t('Bu davrda terminal/karta sotuvi yo‘q')} />
              ) : (
                <div className="table-wrap">
                  <table className="tbl">
                    <thead>
                      <tr>
                        <th>{t('Sana')}</th>
                        <th className="num">{t('Terminal (so‘m)')}</th>
                        <th className="num">{t('POS karta (so‘m)')}</th>
                        <th className="num">{t('Farq')}</th>
                        <th>{t('Holat')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.terminal.map((d) => (
                        <tr key={d.date}>
                          <td className="nowrap">{formatDate(d.date)}</td>
                          <td className="num mono">{som(d.terminalSom)}</td>
                          <td className="num mono">{som(d.posCardSom)}</td>
                          <td className="num mono"
                              style={{ color: Number(d.diffSom) !== 0 ? 'var(--amber)' : undefined }}>
                            {som(d.diffSom)}
                          </td>
                          <td>
                            <span className={`badge ${d.status === 'BALANCED' ? 'badge-naqd' : 'badge-qarzga'}`}>
                              {d.status === 'BALANCED' ? t('Mos') : t('Nomuvofiq')}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </>
        )}
      </Loader>
    </>
  );
}

function SubscriptionCard({ billing }) {
  const t = useT();
  const s = billing.data;
  return (
    <div className="card card-pad section flex-between" style={{ flexWrap: 'wrap', gap: 12 }}>
      <div>
        <div style={{ fontWeight: 700 }}>📅 {t('Obuna (subscription)')}</div>
        <div className="faint" style={{ fontSize: 13 }}>
          {t('Tarif to‘lovi va amal qilish muddati')}
        </div>
      </div>
      {!s ? (
        <div className="faint">{t('Obuna ma‘lumoti mavjud emas')}</div>
      ) : (
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontWeight: 700 }}>{s.plan || '—'}</div>
          <div className="mono" style={{ fontSize: 13 }}>{s.subscriptionExpires || '—'}</div>
          <div style={{ fontWeight: 700, fontSize: 13, color: s.expired ? 'var(--red)' : 'var(--green)' }}>
            {s.expired ? t('Muddati tugagan') : `${s.daysRemaining ?? '—'} ${t('kun qoldi')}`}
          </div>
        </div>
      )}
    </div>
  );
}
