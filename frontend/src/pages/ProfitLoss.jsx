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
 * Profit & Loss ("Foyda va zarar") — derived from the ledger: revenue − COGS
 * = gross profit, − operating expenses = net profit. Values are stored in USD;
 * the page toggle converts them for display. (Display only — no formula change.)
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
                          currencyCode={cur}
                          sub={marginSub(data.grossProfit, data.revenueTotal, t('yalpi marja'))} />
              <MetricCard tone={Number(data.netProfit) >= 0 ? 'green' : 'red'} icon="💰"
                          label={t('Sof foyda')}
                          value={convertMoney(data.netProfit, 'USD', cur, rate)}
                          currencyCode={cur}
                          sub={marginSub(data.netProfit, data.revenueTotal, t('sof marja'))} />
            </div>

            <ProfitComposition data={data} fmt={fmt} t={t} />

            <div className="card pl-statement">
              <div className="card-head">
                <h2>{t('Foyda hisoboti')}</h2>
                <span className="hint">{t('Davr yakuni')}</span>
              </div>
              <div className="card-pad">
                <Section title={t('Daromad')} icon="🛒" lines={data.revenue} fmt={fmt} />
                <PnlTotal icon="🛒" label={t('Jami daromad')} value={fmt(data.revenueTotal)} tone="green" />

                <Section title={t('Tovar tannarxi')} icon="📦" lines={data.cogs} fmt={fmt}
                         sign="− " tone="red" />
                <PnlTotal icon="📈" label={t('Yalpi foyda')} value={fmt(data.grossProfit)} tone="green" />

                <Section title={t('Operatsion xarajatlar')} icon="🧾" lines={data.expenses} fmt={fmt}
                         sign="− " tone="red" />

                <div className="pl-grand">
                  <span className="pl-grand-l">
                    <span className="pl-grand-ico">💰</span>{t('Sof foyda')}
                  </span>
                  <span className="pl-grand-v mono" style={{
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

/** Compact "X% margin" sub-label for a KPI; empty when revenue is zero. */
function marginSub(part, revenue, label) {
  const rev = Number(revenue) || 0;
  if (rev <= 0) return undefined;
  return `${((Number(part) || 0) / rev * 100).toFixed(1)}% ${label}`;
}

/**
 * Revenue composition — a real, data-driven breakdown of where each so'm of
 * revenue goes (COGS + operating expenses + net profit ≈ revenue). Premium
 * finance visual built only from values already on the P&L payload.
 */
function ProfitComposition({ data, fmt, t }) {
  const revenue = Number(data.revenueTotal) || 0;
  const cogs = Number(data.cogsTotal) || 0;
  const gross = Number(data.grossProfit) || 0;
  const net = Number(data.netProfit) || 0;
  const opex = gross - net;               // operating expenses (derived, no new formula)

  if (revenue <= 0) {
    return (
      <div className="card section">
        <div className="card-head"><h2>{t('Foyda tarkibi')}</h2></div>
        <div className="card-pad">
          <EmptyState icon="📈" text={t('Bu davrda daromad yo‘q')}
                      hint={t('Boshqa davrni tanlang yoki sotuv kiriting')} />
        </div>
      </div>
    );
  }

  const grossMargin = (gross / revenue) * 100;
  const netMargin = (net / revenue) * 100;
  const pct = (v) => `${Math.max(0, (v / revenue) * 100).toFixed(1)}%`;
  const segs = [
    { key: 'cogs', label: t('Tovar tannarxi'), value: cogs, cls: 'red' },
    { key: 'opex', label: t('Operatsion xarajatlar'), value: opex, cls: 'amber' },
    { key: 'net', label: t('Sof foyda'), value: net, cls: net >= 0 ? 'green' : 'red' },
  ];

  return (
    <div className="card section pl-comp">
      <div className="card-head">
        <h2>{t('Foyda tarkibi')}</h2>
        <span className="hint">{t('Daromaddan ulushlar')}</span>
      </div>
      <div className="card-pad">
        <div className="pl-comp-top">
          <div className="pl-comp-metric">
            <div className="pl-comp-k">{t('Yalpi marja')}</div>
            <div className="pl-comp-v" style={{ color: 'var(--green)' }}>{grossMargin.toFixed(1)}%</div>
          </div>
          <div className="pl-comp-metric">
            <div className="pl-comp-k">{t('Sof marja')}</div>
            <div className="pl-comp-v" style={{ color: net >= 0 ? 'var(--green)' : 'var(--red)' }}>
              {netMargin.toFixed(1)}%
            </div>
          </div>
          <div className="pl-comp-metric">
            <div className="pl-comp-k">{t('Jami daromad')}</div>
            <div className="pl-comp-v">{fmt(revenue)}</div>
          </div>
        </div>

        <div className="pl-comp-bar">
          {net >= 0
            ? segs.filter((s) => s.value > 0).map((s) => (
              <span key={s.key} className={`pl-seg pl-${s.cls}`}
                    style={{ width: pct(s.value) }} title={`${s.label}: ${fmt(s.value)}`} />
            ))
            : <span className="pl-seg pl-red" style={{ width: '100%' }}
                    title={`${t('Zarar')}: ${fmt(net)}`} />}
        </div>

        <div className="pl-comp-legend">
          {segs.map((s) => (
            <div key={s.key} className="pl-comp-leg">
              <span className={`pl-dot pl-${s.cls}`} />
              <span className="pl-comp-leg-l">{s.label}</span>
              <span className="pl-comp-leg-v mono">{fmt(s.value)}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function Section({ title, icon, lines, fmt, sign = '', tone }) {
  if (!lines || lines.length === 0) return null;
  return (
    <div className="pl-sec">
      <div className="pl-sec-h">
        {icon && <span className="pl-sec-ico">{icon}</span>}
        {title.toUpperCase()}
      </div>
      {lines.map((l) => (
        <PnlRow key={l.code} label={`${l.code} · ${l.name}`} value={fmt(l.amount)}
                sign={sign} tone={tone} muted />
      ))}
    </div>
  );
}

function PnlRow({ label, value, sign = '', tone, muted }) {
  return (
    <div className="pl-row">
      <span className="pl-row-l" style={{ color: muted ? 'var(--text-soft)' : undefined }}>
        {label}
      </span>
      <span className="pl-row-v mono" style={{ color: tone ? `var(--${tone})` : undefined }}>
        {sign}{value}
      </span>
    </div>
  );
}

/** Subtotal row — separated, with an icon and a coloured amount. */
function PnlTotal({ icon, label, value, tone }) {
  return (
    <div className="pl-total">
      <span className="pl-total-l">
        {icon && <span className="pl-total-ico">{icon}</span>}{label}
      </span>
      <span className="pl-total-v mono" style={{ color: tone ? `var(--${tone})` : undefined }}>
        {value}
      </span>
    </div>
  );
}
