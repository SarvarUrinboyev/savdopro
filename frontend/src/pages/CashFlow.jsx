import { useState } from 'react';
import { AccountingApi } from '../api/endpoints.js';
import { AccountingTabs } from '../components/AccountingTabs.jsx';
import { DateRangeFilter, rangeForPreset } from '../components/DateRangeFilter.jsx';
import { CurrencyToggle, EmptyState, Loader, MetricCard, PageHeader } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { useExchangeRate } from '../hooks/useExchangeRate.js';
import { useStickyState } from '../hooks/useStickyState.js';
import { convertMoney, formatMoney } from '../lib/format.js';

/**
 * Cash flow ("Pul oqimi") — direct method: cash + bank movements over the
 * period, grouped by the activity that moved them. Opening + net change =
 * closing.
 */
export function CashFlow() {
  const t = useT();
  const rate = useExchangeRate();
  const [range, setRange] = useState({ preset: 'month', ...rangeForPreset('month') });
  const [cur, setCur] = useStickyState('barakat.cur.acct', 'USD');

  const { data, loading, error, reload } = useApi(
    () => AccountingApi.cashFlow({ from: range.from, to: range.to }),
    [range.from, range.to],
  );

  const fmt = (u) => formatMoney(convertMoney(u, 'USD', cur, rate), cur);

  return (
    <>
      <PageHeader title={t('Pul oqimi')}
                  desc={t('Naqd va bankdagi pul harakati (tanlangan davr)')}>
        <CurrencyToggle value={cur} onChange={setCur} />
      </PageHeader>

      <AccountingTabs />
      <DateRangeFilter value={range} onChange={setRange} />

      <Loader loading={loading} error={error} onRetry={reload}>
        {data && (
          <>
            <div className="metrics-4 section">
              <MetricCard tone="blue" icon="🏦" label={t('Boshlang‘ich qoldiq')}
                          value={convertMoney(data.openingCash, 'USD', cur, rate)} currencyCode={cur} />
              <MetricCard tone="green" icon="⬇️" label={t('Kirim')}
                          value={convertMoney(data.inflowTotal, 'USD', cur, rate)} currencyCode={cur} />
              <MetricCard tone="red" icon="⬆️" label={t('Chiqim')}
                          value={convertMoney(data.outflowTotal, 'USD', cur, rate)} currencyCode={cur} />
              <MetricCard tone={Number(data.closingCash) >= 0 ? 'green' : 'red'} icon="💵"
                          label={t('Yakuniy qoldiq')}
                          value={convertMoney(data.closingCash, 'USD', cur, rate)} currencyCode={cur} />
            </div>

            <div className="grid grid-2">
              <FlowCard title={t('Pul kirimi')} lines={data.inflows} total={data.inflowTotal}
                        fmt={fmt} tone="green" />
              <FlowCard title={t('Pul chiqimi')} lines={data.outflows} total={data.outflowTotal}
                        fmt={fmt} tone="red" />
            </div>

            <div className="card card-pad section flex-between" style={{ flexWrap: 'wrap', gap: 8 }}>
              <span style={{ fontWeight: 700 }}>{t('Sof o‘zgarish')}</span>
              <span className="mono" style={{ fontWeight: 800, fontSize: 18,
                color: Number(data.netChange) >= 0 ? 'var(--green)' : 'var(--red)' }}>
                {fmt(data.netChange)}
              </span>
            </div>
          </>
        )}
      </Loader>
    </>
  );
}

function FlowCard({ title, lines, total, fmt, tone }) {
  const t = useT();
  return (
    <div className="card">
      <div className="card-head"><h2>{title}</h2></div>
      {(!lines || lines.length === 0) ? (
        <EmptyState icon="💸" text={t('Bu davrda harakat yo‘q')} />
      ) : (
        <div className="card-pad">
          {lines.map((l) => (
            <div key={l.label} className="flex-between"
                 style={{ padding: '9px 0', borderBottom: '1px solid var(--border)' }}>
              <span style={{ fontSize: 14 }}>{l.label}</span>
              <span className="mono" style={{ fontWeight: 600 }}>{fmt(l.amount)}</span>
            </div>
          ))}
          <div className="flex-between" style={{ padding: '12px 0 2px' }}>
            <span style={{ fontWeight: 800 }}>{t('Jami')}</span>
            <span className="mono" style={{ fontWeight: 800, fontSize: 16, color: `var(--${tone})` }}>
              {fmt(total)}
            </span>
          </div>
        </div>
      )}
    </div>
  );
}
