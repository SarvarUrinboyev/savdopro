import { useState } from 'react';
import { AccountingApi } from '../api/endpoints.js';
import { AccountingTabs } from '../components/AccountingTabs.jsx';
import { DateRangeFilter, rangeForPreset } from '../components/DateRangeFilter.jsx';
import { CurrencyToggle, Loader, MetricCard, PageHeader } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { useExchangeRate } from '../hooks/useExchangeRate.js';
import { useStickyState } from '../hooks/useStickyState.js';
import { convertMoney, formatMoney } from '../lib/format.js';

/**
 * Profit & Loss ("Foyda va zarar") — derived from the ledger: revenue − COGS
 * = gross profit, − operating expenses = net profit. Values are stored in USD;
 * the page toggle converts them for display.
 */
export function ProfitLoss() {
  const t = useT();
  const rate = useExchangeRate();
  const [range, setRange] = useState({ preset: 'month', ...rangeForPreset('month') });
  const [cur, setCur] = useStickyState('barakat.cur.acct', 'USD');

  const { data, loading, error, reload } = useApi(
    () => AccountingApi.profitLoss({ from: range.from, to: range.to }),
    [range.from, range.to],
  );

  const fmt = (u) => formatMoney(convertMoney(u, 'USD', cur, rate), cur);

  return (
    <>
      <PageHeader title={t('Foyda va zarar')}
                  desc={t('Tanlangan davr uchun daromad, tannarx va xarajatlar (Bosh kitobdan)')}>
        <CurrencyToggle value={cur} onChange={setCur} />
      </PageHeader>

      <AccountingTabs />
      <DateRangeFilter value={range} onChange={setRange} />

      <Loader loading={loading} error={error} onRetry={reload}>
        {data && (
          <>
            <div className="metrics-4 section">
              <MetricCard tone="blue" icon="🛒" label={t('Sof daromad')}
                          value={convertMoney(data.revenueTotal, 'USD', cur, rate)}
                          currencyCode={cur} />
              <MetricCard tone="amber" icon="📦" label={t('Tovar tannarxi')}
                          value={convertMoney(data.cogsTotal, 'USD', cur, rate)}
                          currencyCode={cur} />
              <MetricCard tone="green" icon="📈" label={t('Yalpi foyda')}
                          value={convertMoney(data.grossProfit, 'USD', cur, rate)}
                          currencyCode={cur} />
              <MetricCard tone={Number(data.netProfit) >= 0 ? 'green' : 'red'} icon="💰"
                          label={t('Sof foyda')}
                          value={convertMoney(data.netProfit, 'USD', cur, rate)}
                          currencyCode={cur} />
            </div>

            <div className="card">
              <div className="card-head"><h2>{t('Foyda hisoboti')}</h2></div>
              <div className="card-pad">
                <Section title={t('Daromad')} lines={data.revenue} fmt={fmt} />
                <PnlRow label={t('Jami daromad')} value={fmt(data.revenueTotal)} strong tone="green" />
                <Section title={t('Tovar tannarxi')} lines={data.cogs} fmt={fmt} sign="− " tone="red" />
                <PnlRow label={t('Yalpi foyda')} value={fmt(data.grossProfit)} strong tone="green" />
                <Section title={t('Operatsion xarajatlar')} lines={data.expenses} fmt={fmt}
                         sign="− " tone="red" />
                <div className="flex-between" style={{ padding: '14px 0 2px' }}>
                  <span style={{ fontWeight: 800, fontSize: 15 }}>{t('Sof foyda')}</span>
                  <span className="mono" style={{
                    fontWeight: 800, fontSize: 19,
                    color: Number(data.netProfit) >= 0 ? 'var(--green)' : 'var(--red)',
                  }}>
                    {fmt(data.netProfit)}
                  </span>
                </div>
              </div>
            </div>
          </>
        )}
      </Loader>
    </>
  );
}

function Section({ title, lines, fmt, sign = '', tone }) {
  if (!lines || lines.length === 0) return null;
  return (
    <>
      <div style={{ fontSize: 12, fontWeight: 700, letterSpacing: '.05em',
                    color: 'var(--text-faint)', margin: '12px 0 4px' }}>
        {title.toUpperCase()}
      </div>
      {lines.map((l) => (
        <PnlRow key={l.code} label={`${l.code} · ${l.name}`} value={fmt(l.amount)}
                sign={sign} tone={tone} muted />
      ))}
    </>
  );
}

function PnlRow({ label, value, sign = '', strong, tone, muted }) {
  return (
    <div className="flex-between" style={{ padding: '9px 0', borderBottom: '1px solid var(--border)' }}>
      <span style={{ fontWeight: strong ? 700 : 500, color: muted ? 'var(--text-soft)' : undefined,
                     fontSize: muted ? 13 : undefined }}>
        {label}
      </span>
      <span className="mono" style={{ fontWeight: strong ? 800 : 600,
                                      color: tone ? `var(--${tone})` : undefined }}>
        {sign}{value}
      </span>
    </div>
  );
}
