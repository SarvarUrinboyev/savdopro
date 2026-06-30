import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  BalanceApi, DashboardApi, ExchangeRateApi, ManagementApi, PaymentApi, ProductApi,
} from '../api/endpoints.js';
import { AnomalyBanner } from '../components/AnomalyBanner.jsx';
import { AnomalyHistory } from '../components/AnomalyHistory.jsx';
import { CashboxForecastCard } from '../components/CashboxForecastCard.jsx';
import { OnboardingChecklist } from '../components/OnboardingChecklist.jsx';
import { LiveSalesFeed } from '../components/LiveSalesFeed.jsx';
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

/**
 * Compact info strip — demotes the old big dollar-rate card and the big
 * "Ertalabgi balans" block into small chips, plus a quick link to the period
 * finance report. No business logic — just a leaner header band.
 */
function DashInfoBar({ rate, startingCash, onEditBalance }) {
  const t = useT();
  return (
    <div className="dash-infobar section">
      <div className="dib-chip">
        <span className="dib-ico">💵</span>
        <div className="dib-body">
          <div className="dib-k">{t('Dollar kursi')} · {t('Markaziy bank')}</div>
          <div className="dib-v">
            {rate && rate.available ? (
              <>1 USD = {money(Math.round(Number(rate.rate)))} {t("so'm")}</>
            ) : (
              <span className="faint">{t("Internetga ulanib bo'lmadi")}</span>
            )}
          </div>
        </div>
      </div>
      <div className="dib-chip">
        <span className="dib-ico">🌅</span>
        <div className="dib-body">
          <div className="dib-k">{t('Ertalabgi balans')}</div>
          <div className="dib-v">{usd(startingCash)}</div>
        </div>
        <button className="dib-edit" onClick={onEditBalance} title={t('Tahrirlash')} aria-label={t('Tahrirlash')}>
          ✏️
        </button>
      </div>
      <Link to="/management" className="btn btn-ghost btn-sm dib-report"
            title={t('Davr bo‘yicha foyda, xarajat va eksport')}>
        📊 {t('Moliya hisoboti (davr)')} →
      </Link>
    </div>
  );
}

function Content({ data, rate, onEditBalance }) {
  const t = useT();
  return (
    <>
      <div className="dboard-hero">
        <div>
          <div className="dboard-hero-eyebrow">SavdoPRO · {t('Boshqaruv')}</div>
          <h1 className="dboard-hero-title">{t("Do'kon boshqaruv markazi")}</h1>
          <p className="dboard-hero-sub">
            {t('Bugungi savdo, foyda, qarz va kassa — barchasi bitta ekranda, jonli.')}
          </p>
        </div>
        <Link to="/pos" className="btn btn-primary">💳 {t('Kassada sotuv')}</Link>
      </div>

      <DashInfoBar rate={rate} startingCash={data.startingCash} onEditBalance={onEditBalance} />

      <AnomalyBanner />

      {/* Owner control-centre KPIs — headline numbers right under the hero. */}
      <KpiGrid data={data} />

      <OnboardingChecklist />
      <LiveSalesFeed />

      <div className="grid grid-2 section">
        <SalesChart />
        <ExpenseBreakdown data={data} />
      </div>

      <CashboxForecastCard />

      <div className="grid grid-3 section">
        <LowStockCard />
        <ActivityFeed />
        <TopExpensesCard data={data} />
      </div>

      <div className="card section">
        <div className="card-head">
          <h2>{t('Buyurtmalar holati')}</h2>
        </div>
        <div className="card-pad">
          <OrderGroup tag="tag-red" title={t('Bugun keladi')} orders={data.ordersToday} />
          <OrderGroup tag="tag-amber" title={t('Ertaga keladi')} orders={data.ordersTomorrow} />
          <OrderGroup tag="tag-red" title={t('Kelmagan')} orders={data.ordersOverdue} last />
        </div>
      </div>

      <AnomalyHistory />
    </>
  );
}

/**
 * Owner control-centre headline KPIs. Sales & net profit come from the REAL
 * sold-goods report (totalRevenue / totalProfit = revenue − COGS); net profit
 * then subtracts today's operating expenses from the dashboard payload. Debt
 * and cash balance come straight off the payload. The sold-goods endpoint is
 * the same one the dashboard already uses (role-safe), and a failure degrades
 * only the two sales cards — debt & cash still render.
 */
function KpiGrid({ data }) {
  const t = useT();
  const today = todayIso();
  const { data: report, loading, error } = useApi(
    () => ManagementApi.soldGoods({ from: today, to: today }),
    [],
  );
  const ok = !!report && !error;
  const revenue = ok ? Number(report.totalRevenue) || 0 : 0;
  const grossProfit = ok ? Number(report.totalProfit) || 0 : 0;        // revenue − COGS
  const netProfit = grossProfit - (Number(data.todayExpenseTotal) || 0); // − operating expenses
  const pending = loading ? t('Yuklanmoqda…') : undefined;

  return (
    <div className="metrics section">
      <MetricCard tone="blue" icon="💳" label={t('Bugungi savdo')}
                  value={revenue} tag="SAVDO" sub={pending} />
      <MetricCard tone={netProfit >= 0 ? 'green' : 'red'} icon="💰" label={t('Sof foyda')}
                  value={ok ? netProfit : 0} tag="FOYDA"
                  sub={pending || t('Sotuv − tannarx − xarajat')} />
      <MetricCard tone="red" icon="📒" label={t('Mijoz qarzlari')} value={data.totalDebt} tag="QARZ" />
      <MetricCard tone="amber" icon="🏦" label={t('Kassa qoldiq')} value={data.estimatedCash} tag="KASSA" />
    </div>
  );
}

/** Compact "Tugayotgan mahsulotlar" card with a healthy empty state. */
function LowStockCard() {
  const t = useT();
  const { data, loading } = useApi(() => ProductApi.lowStock(), []);
  const items = Array.isArray(data) ? data : [];

  return (
    <div className="card feed-card">
      <div className="feed-head">
        <h3>⚠️ {t('Tugayotgan mahsulotlar')}</h3>
        {items.length > 0 && <span className="ls-count">{items.length}</span>}
      </div>
      <div className="feed-body">
        {loading ? (
          <Spinner />
        ) : items.length === 0 ? (
          <EmptyState icon="✅" text={t('Hammasi joyida')} hint={t('Kam zaxira mahsulot yo‘q')} />
        ) : (
          <>
            {items.slice(0, 6).map((p) => (
              <Link key={p.id} to={`/warehouse/${p.id}`} className="ls-row">
                <span className="feed-text">
                  <span className="feed-desc">{p.name}</span>
                  <span className="feed-meta">
                    {t('Qoldiq')}: {p.quantity} · {t('Pol')}: {p.lowStockThreshold ?? '—'}
                  </span>
                </span>
                <span className={`ls-pill${p.quantity === 0 ? ' zero' : ''}`}>
                  {p.quantity === 0 ? t('Tugadi') : p.quantity}
                </span>
              </Link>
            ))}
            {items.length > 6 && (
              <Link to="/warehouse" className="ls-more">
                +{items.length - 6} {t('boshqa')} · {t('Omborni ko‘rish')} →
              </Link>
            )}
          </>
        )}
      </div>
    </div>
  );
}

/** Today's biggest expenses, as a card for the 3-up section. */
function TopExpensesCard({ data }) {
  const t = useT();
  return (
    <div className="card feed-card">
      <div className="feed-head">
        <h3>🧾 {t('Bugungi xarajatlar')}</h3>
        <span className="hint">{t('Eng katta')}</span>
      </div>
      <div className="feed-body">
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
          <h3>{t('Savdo dinamikasi (7 kun)')}</h3>
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

/**
 * Today's outgoing money split by payment method, as a CSS/SVG donut. Reads the
 * real expense-by-method totals already on the dashboard payload (todayNaqd /
 * todayKarta / todayKassa are EXPENSES bucketed by method per DashboardService;
 * no extra request, no fabricated numbers). Safe empty state when nothing went
 * out yet.
 */
function ExpenseBreakdown({ data }) {
  const t = useT();
  const segs = [
    { key: 'naqd',  label: t('Naqd'),  value: Number(data.todayNaqd) || 0,  color: 'var(--green)' },
    { key: 'karta', label: t('Karta'), value: Number(data.todayKarta) || 0, color: 'var(--blue)' },
    { key: 'kassa', label: t('Kassa'), value: Number(data.todayKassa) || 0, color: 'var(--amber)' },
  ];
  const total = segs.reduce((sum, s) => sum + s.value, 0);
  const R = 54;
  const C = 2 * Math.PI * R;
  let acc = 0;

  return (
    <div className="card pshare-card">
      <div className="card-head">
        <h2>{t('Xarajat taqsimoti')}</h2>
        <span className="hint">{t('Bugun · usul bo‘yicha')}</span>
      </div>
      <div className="card-pad">
        {total === 0 ? (
          <EmptyState icon="💸" text={t('Bugun xarajat bo‘lmadi')} />
        ) : (
          <div className="pshare">
            <svg viewBox="0 0 140 140" className="pshare-svg" aria-hidden="true">
              <circle cx="70" cy="70" r={R} fill="none" stroke="var(--surface-2)" strokeWidth="15" />
              {segs.filter((s) => s.value > 0).map((s) => {
                const frac = s.value / total;
                const el = (
                  <circle
                    key={s.key}
                    cx="70" cy="70" r={R} fill="none"
                    stroke={s.color} strokeWidth="15"
                    strokeDasharray={`${(frac * C).toFixed(2)} ${C.toFixed(2)}`}
                    strokeDashoffset={`${(-acc * C).toFixed(2)}`}
                    transform="rotate(-90 70 70)"
                  />
                );
                acc += frac;
                return el;
              })}
              <text x="70" y="66" textAnchor="middle" className="pshare-total-v">{usd(total)}</text>
              <text x="70" y="86" textAnchor="middle" className="pshare-total-l">{t('Jami')}</text>
            </svg>
            <div className="pshare-legend">
              {segs.map((s) => (
                <div key={s.key} className="pshare-leg">
                  <span className="pshare-dot" style={{ background: s.color }} />
                  <span className="pshare-leg-label">{s.label}</span>
                  <span className="pshare-leg-val mono">
                    {usd(s.value)} · {Math.round((s.value / total) * 100)}%
                  </span>
                </div>
              ))}
            </div>
          </div>
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
        <h3>{t('So‘nggi faoliyat')}</h3>
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
