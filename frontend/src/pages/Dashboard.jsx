import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  BalanceApi, DashboardApi, ExchangeRateApi, ManagementApi, PaymentApi,
} from '../api/endpoints.js';
import { AnomalyBanner } from '../components/AnomalyBanner.jsx';
import { AnomalyHistory } from '../components/AnomalyHistory.jsx';
import { CashboxForecastCard } from '../components/CashboxForecastCard.jsx';
import { OnboardingChecklist } from '../components/OnboardingChecklist.jsx';
import { LiveSalesFeed } from '../components/LiveSalesFeed.jsx';
import { LowStockWidget } from '../components/LowStockWidget.jsx';
import { Modal } from '../components/Modal.jsx';
import { useToast } from '../components/Toast.jsx';
import {
  EmptyState, Loader, MetricCard, ProgressBar, Spinner,
} from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { formatDate, formatTime, money, shiftIso, todayIso, usd } from '../lib/format.js';

export function Dashboard() {
  const { data, loading, error, reload } = useApi(
    () => Promise.all([DashboardApi.today(), ExchangeRateApi.get()]),
    [],
  );
  const [editing, setEditing] = useState(false);
  const dashboard = data ? data[0] : null;
  const rate = data ? data[1] : null;

  return (
    <>
      <Loader loading={loading} error={error} onRetry={reload}>
        {data && (
          <Content data={dashboard} rate={rate} onEditBalance={() => setEditing(true)} />
        )}
      </Loader>
      {editing && (
        <BalanceModal
          current={dashboard?.startingCash}
          onClose={() => setEditing(false)}
          onSaved={() => {
            setEditing(false);
            reload();
          }}
        />
      )}
    </>
  );
}

function RateBanner({ rate }) {
  const t = useT();
  return (
    <div
      className="card card-pad section flex-between"
      style={{ borderLeft: '4px solid var(--blue)', flexWrap: 'wrap', gap: 8 }}
    >
      <span style={{ fontWeight: 600 }}>💵 {t('Bugungi dollar kursi')}</span>
      {rate && rate.available ? (
        <span>
          <b style={{ fontSize: 16 }}>
            1 USD = {money(Math.round(Number(rate.rate)))} {t("so'm")}
          </b>
          <span className="faint">
            {' '}&middot; {t('Markaziy bank')} &middot; {formatDate(rate.date)}
          </span>
        </span>
      ) : (
        <span className="faint">{t("Internetga ulanib bo'lmadi — kursni keyinroq ko'ring")}</span>
      )}
    </div>
  );
}

function Content({ data, rate, onEditBalance }) {
  const t = useT();
  return (
    <>
      <div className="section flex-between" style={{ flexWrap: 'wrap', gap: 8, marginBottom: 4 }}>
        <span style={{ fontWeight: 600, color: 'var(--text-faint)', fontSize: 13 }}>
          ⚡ {t('Bugungi jonli holat — kassa, savdo va buyurtmalar')}
        </span>
        <Link to="/management" className="btn btn-ghost btn-sm"
              title={t('Davr bo‘yicha foyda, xarajat va eksport')}>
          📊 {t('Moliya hisoboti (davr)')} →
        </Link>
      </div>
      <OnboardingChecklist />
      <RateBanner rate={rate} />
      <AnomalyBanner />
      <LiveSalesFeed />
      <LowStockWidget />
      <CashboxForecastCard />
      <div className="balance-hero section">
        <div>
          <div className="bh-label">{t('ERTALABGI BALANS')}</div>
          <div className="bh-value">{usd(data.startingCash)}</div>
          <button className="btn btn-ghost btn-sm mt-8" onClick={onEditBalance}>
            ✏️ {t('Tahrirlash')}
          </button>
        </div>
        <div className="bh-side">
          <div className="s-label">{t('Taxminiy qoldiq')}</div>
          <div className="s-value">{usd(data.estimatedCash)}</div>
        </div>
      </div>

      <div className="metrics section">
        <MetricCard tone="red"    icon="🧾" label={t('Bugungi xarajat')}    value={data.todayExpenseTotal} tag="CHIQIM" />
        <MetricCard tone="amber"  icon="🏦" label={t('Kassadan')}           value={data.todayKassa}        tag="KASSADA" />
        <MetricCard tone="green"  icon="💵" label={t('Naqddan')}            value={data.todayNaqd}         tag="NAQD" />
        <MetricCard tone="blue"   icon="💳" label={t('Kartadan')}           value={data.todayKarta}        tag="KARTA" />
        <MetricCard tone="red"    icon="📒" label={t('Umumiy qarz')}        value={data.totalDebt}         tag="QARZ" />
      </div>

      <div className="grid grid-2 section">
        <SalesChart />
        <ActivityFeed />
      </div>

      <div className="grid grid-2">
        <div className="card">
          <div className="card-head">
            <h2>{t('Bugungi xarajatlar')}</h2>
            <span className="hint">{t('Eng katta xarajatlar')}</span>
          </div>
          <div className="card-pad">
            {data.topExpenses.length === 0 ? (
              <EmptyState icon="🧾" text={t('Bugun hali xarajat kiritilmagan')} />
            ) : (
              <div className="list-stack">
                {data.topExpenses.map((e, i) => (
                  <div key={i}>
                    <div className="flex-between" style={{ marginBottom: 5 }}>
                      <span style={{ fontWeight: 600 }}>{e.name}</span>
                      <span className="mono">{usd(e.amount)}</span>
                    </div>
                    <ProgressBar percent={e.percent} />
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        <div className="card">
          <div className="card-head">
            <h2>{t('Buyurtmalar holati')}</h2>
          </div>
          <div className="card-pad">
            <OrderGroup tag="tag-red" title={t('Bugun keladi')} orders={data.ordersToday} />
            <OrderGroup tag="tag-amber" title={t('Ertaga keladi')} orders={data.ordersTomorrow} />
            <OrderGroup tag="tag-red" title={t('Kelmagan')} orders={data.ordersOverdue} last />
          </div>
        </div>
      </div>

      <AnomalyHistory />
    </>
  );
}

function OrderGroup({ tag, title, orders, last }) {
  const t = useT();
  return (
    <div style={{ marginBottom: last ? 0 : 18 }}>
      <div className="section-label">
        <span className={`tag ${tag}`} />
        {title}
        <span className="faint" style={{ fontWeight: 500 }}>({orders.length})</span>
      </div>
      {orders.length === 0 ? (
        <div className="faint" style={{ fontSize: 13 }}>{t("Buyurtma yo'q")}</div>
      ) : (
        <div className="list-stack">
          {orders.map((o) => (
            <div key={o.id} className="flex-between" style={{ fontSize: 13 }}>
              <span style={{ fontWeight: 600 }}>{o.name}</span>
              <span className="faint">{formatDate(o.deliveryDate)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/** 7-day sales-flow line chart, built from the sold-goods report. */
function SalesChart() {
  const t = useT();
  const { data: report, loading } = useApi(
    () => ManagementApi.soldGoods({ from: shiftIso(-6), to: todayIso() }),
    [],
  );

  // Always build a 7-day series, even while loading, so the layout is stable.
  const days = [];
  for (let i = 6; i >= 0; i -= 1) {
    days.push({ date: shiftIso(-i), total: 0 });
  }
  if (report && Array.isArray(report.lines)) {
    report.lines.forEach((line) => {
      const d = String(line.soldAt).slice(0, 10);
      const day = days.find((x) => x.date === d);
      if (day) {
        day.total += Number(line.lineRevenue) || 0;
      }
    });
  }

  const W = 520;
  const H = 180;
  const PX = 40;          // left padding for Y labels
  const PY_TOP = 16;
  const PY_BOTTOM = 30;   // bottom padding for X labels
  const plotH = H - PY_TOP - PY_BOTTOM;
  const maxVal = Math.max(...days.map((d) => d.total), 1);
  const baseY = H - PY_BOTTOM;
  const points = days.map((d, i) => ({
    x: PX + (i / (days.length - 1)) * (W - PX - 16),
    y: baseY - (d.total / maxVal) * plotH,
  }));
  const linePath = points
    .map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`)
    .join(' ');
  const last = points[points.length - 1];
  const first = points[0];
  const fillPath = `${linePath} L ${last.x.toFixed(1)} ${baseY} L ${first.x.toFixed(1)} ${baseY} Z`;
  const isEmpty = days.every((d) => d.total === 0);
  // Uzbek day-of-week short labels: Yak, Du, Se, Ch, Pa, Ju, Sha
  const DAYS = ['Yak', 'Du', 'Se', 'Ch', 'Pa', 'Ju', 'Sha'];
  const dayLabel = (iso) => DAYS[new Date(`${iso}T00:00:00`).getDay()];
  const yMid = baseY - plotH / 2;
  const fmtAxis = (n) => {
    if (n >= 1000) return `$${(n / 1000).toFixed(n >= 10000 ? 0 : 1)}k`;
    return `$${Math.round(n)}`;
  };

  return (
    <div className="card chart-card">
      <div className="chart-head">
        <div>
          <span className="chart-eyebrow">{t('Tijorat tranzaksiyalari')}</span>
          <h3>{t('Sotuvlar oqimi (7 kun)')}</h3>
        </div>
      </div>
      <div className="chart-body">
        {loading ? (
          <Spinner />
        ) : isEmpty ? (
          <div className="chart-empty">{t("Bu davrda sotilgan tovar yo'q")}</div>
        ) : (
          <svg
            viewBox={`0 0 ${W} ${H}`}
            className="chart-svg"
          >
            <defs>
              <linearGradient id="dashChartFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#10b981" stopOpacity="0.28" />
                <stop offset="100%" stopColor="#10b981" stopOpacity="0" />
              </linearGradient>
            </defs>
            {/* horizontal grid + Y labels */}
            <line x1={PX} y1={PY_TOP} x2={W - 8} y2={PY_TOP}
                  stroke="currentColor" strokeOpacity="0.08" strokeDasharray="3 4" />
            <line x1={PX} y1={yMid} x2={W - 8} y2={yMid}
                  stroke="currentColor" strokeOpacity="0.08" strokeDasharray="3 4" />
            <line x1={PX} y1={baseY} x2={W - 8} y2={baseY}
                  stroke="currentColor" strokeOpacity="0.18" />
            <text x={PX - 6} y={PY_TOP + 4} className="chart-axis"
                  textAnchor="end">{fmtAxis(maxVal)}</text>
            <text x={PX - 6} y={yMid + 4} className="chart-axis"
                  textAnchor="end">{fmtAxis(maxVal / 2)}</text>
            <text x={PX - 6} y={baseY + 4} className="chart-axis"
                  textAnchor="end">$0</text>
            {/* fill + line */}
            <path d={fillPath} fill="url(#dashChartFill)" />
            <path
              d={linePath}
              fill="none"
              stroke="#10b981"
              strokeWidth="2.4"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
            {points.map((p, i) => (
              <circle key={i} cx={p.x} cy={p.y} r="3.5" fill="#10b981" />
            ))}
            {/* X labels (day-of-week) */}
            {points.map((p, i) => (
              <text key={`x${i}`} x={p.x} y={H - 8} className="chart-axis"
                    textAnchor="middle">
                {dayLabel(days[i].date)}
              </text>
            ))}
          </svg>
        )}
      </div>
    </div>
  );
}

/** Today's combined activity feed: sales + payments, newest first. */
function ActivityFeed() {
  const t = useT();
  const today = todayIso();
  const { data, loading } = useApi(
    () => Promise.all([
      ManagementApi.soldGoods({ from: today, to: today }),
      PaymentApi.list({ from: today, to: today }),
    ]),
    [],
  );

  const items = [];
  if (data) {
    const [report, paymentResp] = data;
    if (report && Array.isArray(report.lines)) {
      report.lines.forEach((line) => {
        items.push({
          kind: 'sale',
          time: line.soldAt,
          desc: line.quantity > 1
            ? `${line.productName} × ${line.quantity}`
            : line.productName,
          amount: Number(line.lineRevenue) || 0,
          meta: t('Sotuv'),
        });
      });
    }
    const payments = paymentResp && Array.isArray(paymentResp.payments)
      ? paymentResp.payments
      : [];
    payments.forEach((p) => {
      items.push({
        kind: p.direction === 'INCOMING' ? 'in' : 'out',
        time: p.createdAt || `${p.date}T00:00:00`,
        desc: p.party || (p.direction === 'INCOMING' ? t('Kirim') : t('Chiqim')),
        amount: Number(p.amount) || 0,
        meta: p.method || '',
      });
    });
    items.sort((a, b) => String(b.time).localeCompare(String(a.time)));
  }
  const top = items.slice(0, 8);

  return (
    <div className="card feed-card">
      <div className="feed-head">
        <h3>{t('Kassa operatsiyalari')}</h3>
        <span className="feed-live"><span className="dot" /> Live</span>
      </div>
      <div className="feed-body">
        {loading ? (
          <Spinner />
        ) : top.length === 0 ? (
          <EmptyState icon="⚡" text={t("Bugun amaliyot yo'q")} />
        ) : (
          top.map((it, i) => (
            <div key={i} className="feed-row">
              <span className={`feed-dot ${it.kind}`} />
              <div className="feed-text">
                <div className="feed-desc">{it.desc}</div>
                <div className="feed-meta">
                  {formatTime(it.time)}
                  {it.meta ? ` · ${it.meta}` : ''}
                </div>
              </div>
              <div className={`feed-amount ${it.kind === 'out' ? 'neg' : ''}`}>
                {it.kind === 'out' ? '−' : '+'}{usd(it.amount)}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function BalanceModal({ current, onClose, onSaved }) {
  const t = useT();
  const [value, setValue] = useState(current ?? '');
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  const save = async () => {
    const amount = Number(value);
    if (value === '' || Number.isNaN(amount) || amount < 0) {
      toast.error(t("Balansni to'g'ri kiriting"));
      return;
    }
    setBusy(true);
    try {
      await BalanceApi.set({ startingCash: amount });
      toast.success(t('Ertalabgi balans yangilandi'));
      onSaved();
    } catch (err) {
      toast.error(err.message);
      setBusy(false);
    }
  };

  return (
    <Modal
      title={t('Ertalabgi balansni tahrirlash')}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={busy}>
            {t('Bekor qilish')}
          </button>
          <button className="btn btn-primary" onClick={save} disabled={busy}>
            {busy ? t('Saqlanmoqda...') : t('Saqlash')}
          </button>
        </>
      }
    >
      <div className="field">
        <label>{t('Ertalabgi balans (USD)')}</label>
        <input
          className="input"
          type="number"
          inputMode="numeric"
          autoFocus
          value={value}
          onChange={(e) => setValue(e.target.value)}
        />
        <div className="field-hint">{t('Bugun ertalab kassaga olib kelingan naqd pul.')}</div>
      </div>
    </Modal>
  );
}
