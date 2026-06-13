import { useState } from 'react';
import { AccountingApi } from '../api/endpoints.js';
import { AccountingTabs } from '../components/AccountingTabs.jsx';
import { CurrencyToggle, Loader, PageHeader } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { useExchangeRate } from '../hooks/useExchangeRate.js';
import { useStickyState } from '../hooks/useStickyState.js';
import { convertMoney, formatMoney, todayIso } from '../lib/format.js';

/**
 * Balance sheet ("Balans") as of a date: Assets = Liabilities + Equity. Because
 * every ledger entry balances, the two sides match by construction; current
 * net income (un-closed P&L) is shown inside equity to make that explicit.
 */
export function BalanceSheet() {
  const t = useT();
  const rate = useExchangeRate();
  const [asOf, setAsOf] = useState(todayIso());
  const [cur, setCur] = useStickyState('barakat.cur.acct', 'USD');

  const { data, loading, error, reload } = useApi(
    () => AccountingApi.balanceSheet({ asOf }),
    [asOf],
  );

  const fmt = (u) => formatMoney(convertMoney(u, 'USD', cur, rate), cur);

  return (
    <>
      <PageHeader title={t('Balans')} desc={t('Aktivlar = Passivlar + Kapital (tanlangan sanaga)')}>
        <input className="input" type="date" value={asOf}
               onChange={(e) => setAsOf(e.target.value)} style={{ width: 170 }} />
        <CurrencyToggle value={cur} onChange={setCur} />
      </PageHeader>

      <AccountingTabs />

      <Loader loading={loading} error={error} onRetry={reload}>
        {data && (
          <>
            <div className="card card-pad section flex-between" style={{ flexWrap: 'wrap', gap: 8 }}>
              <div style={{ fontWeight: 700 }}>
                {data.balanced ? '✅ ' : '⚠️ '}
                {data.balanced ? t('Balans teng') : t('Balans teng emas')}
              </div>
              <div className="faint mono">
                {t('Aktiv')}: {fmt(data.assetTotal)} · {t('Passiv + Kapital')}: {fmt(data.liabilitiesPlusEquity)}
              </div>
            </div>

            <div className="grid grid-2">
              <SectionCard title={t('Aktivlar')} lines={data.assets} total={data.assetTotal}
                           totalLabel={t('Jami aktivlar')} fmt={fmt} tone="blue" />
              <div>
                <SectionCard title={t('Passivlar (majburiyatlar)')} lines={data.liabilities}
                             total={data.liabilityTotal} totalLabel={t('Jami passivlar')}
                             fmt={fmt} tone="amber" />
                <div style={{ height: 16 }} />
                <SectionCard title={t('Kapital')} lines={data.equity}
                             extra={{ label: t('Joriy davr foydasi'), value: data.netIncomeToDate }}
                             total={data.equityTotal} totalLabel={t('Jami kapital')}
                             fmt={fmt} tone="green" />
              </div>
            </div>
          </>
        )}
      </Loader>
    </>
  );
}

function SectionCard({ title, lines, total, totalLabel, fmt, tone, extra }) {
  return (
    <div className="card">
      <div className="card-head"><h2>{title}</h2></div>
      <div className="card-pad">
        {(lines || []).map((l) => (
          <Row key={l.code} label={`${l.code} · ${l.name}`} value={fmt(l.amount)} />
        ))}
        {extra && Number(extra.value) !== 0 && (
          <Row label={extra.label} value={fmt(extra.value)} muted />
        )}
        {(!lines || lines.length === 0) && !extra && (
          <div className="faint" style={{ padding: '8px 0' }}>—</div>
        )}
        <div className="flex-between" style={{ padding: '12px 0 2px' }}>
          <span style={{ fontWeight: 800 }}>{totalLabel}</span>
          <span className="mono" style={{ fontWeight: 800, fontSize: 16, color: `var(--${tone})` }}>
            {fmt(total)}
          </span>
        </div>
      </div>
    </div>
  );
}

function Row({ label, value, muted }) {
  return (
    <div className="flex-between" style={{ padding: '9px 0', borderBottom: '1px solid var(--border)' }}>
      <span style={{ fontSize: 13, color: muted ? 'var(--text-soft)' : undefined,
                     fontStyle: muted ? 'italic' : undefined }}>{label}</span>
      <span className="mono" style={{ fontWeight: 600 }}>{value}</span>
    </div>
  );
}
