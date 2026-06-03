import { AiApi } from '../api/endpoints.js';
import { Loader } from './ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { money } from '../lib/format.js';

/**
 * 7-day cashbox projection, drawn as a row of 3D "crystal" spikes: each day's
 * height is its projected revenue and the busiest day glows green so the weekly
 * peak reads at a glance. Pure SVG (no chart lib) that scales to the card width.
 */
export function CashboxForecastCard() {
  const t = useT();
  const { data, loading, error, reload } = useApi(() => AiApi.cashboxForecast(), []);
  if (loading || error || !data || !data.daily?.length) {
    return (
      <div className="card section">
        <div className="card-head">
          <h2>🔮 {t('Keyingi 7 kun prognozi')}</h2>
        </div>
        <Loader loading={loading} error={error} onRetry={reload}>
          <div className="empty" style={{ padding: 24 }}>{t('Ma\'lumot yetarli emas (oxirgi 30 kun sotuv kerak)')}</div>
        </Loader>
      </div>
    );
  }

  const days = data.daily;
  const n = days.length;
  const revOf = (d) => Number(d.projectedRevenueUzs) || 0;
  const max = days.reduce((m, d) => Math.max(m, revOf(d)), 1);
  let maxIdx = 0;
  days.forEach((d, i) => { if (revOf(d) > revOf(days[maxIdx])) maxIdx = i; });

  // SVG geometry (viewBox units — the whole thing scales to the card width).
  const W = 1000, H = 170, groundY = 148, topPad = 14, usableH = groundY - topPad;
  const slot = W / n, fw = 52, depth = 18, depthY = 12, capH = 26, minPct = 0.08;

  const spikes = days.map((d, i) => {
    const pct = Math.max(minPct, revOf(d) / max);
    const cx = slot * (i + 0.5);
    const x0 = cx - fw / 2, x1 = cx + fw / 2;
    const topY = groundY - pct * usableH;          // pointed tip
    const shoulderY = Math.min(groundY - 6, topY + capH); // where the roof meets the body
    // Front face = a pointed "house" pentagon; right face = that silhouette
    // extruded up-right by (depth, -depthY) → the 3D body + crystal tip.
    const front = `M${x0},${groundY} L${x0},${shoulderY} L${cx},${topY} L${x1},${shoulderY} L${x1},${groundY} Z`;
    const side = `M${x1},${groundY} L${x1},${shoulderY} L${cx},${topY} `
      + `L${cx + depth},${topY - depthY} L${x1 + depth},${shoulderY - depthY} L${x1 + depth},${groundY - depthY} Z`;
    return { cx, front, side, peak: i === maxIdx };
  });

  return (
    <div className="card section">
      <div className="card-head">
        <h2>🔮 {t('Keyingi 7 kun prognozi')}</h2>
        <span className="hint">
          {t('Jami')}: <strong>{money(data.projectedNext7DaysTotal)} so'm</strong> ·
          ~{data.projectedNext7DaysCount} {t('savdo')}
        </span>
      </div>
      <div className="card-pad">
        <p className="faint" style={{ fontSize: 12, marginBottom: 14 }}>
          {t("Oxirgi 30 kun savdosi asosida har bir hafta kuni uchun bashorat (o'rtacha × hafta-kuni koeffitsienti). Taxminiy — real natija farq qilishi mumkin.")}
        </p>

        <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', height: 'auto', display: 'block', overflow: 'visible' }}
             role="img" aria-label={t('Keyingi 7 kun prognozi')}>
          <defs>
            <linearGradient id="f3dFront" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#bfdbfe" />
              <stop offset="50%" stopColor="#3b82f6" />
              <stop offset="100%" stopColor="#1d4ed8" />
            </linearGradient>
            <linearGradient id="f3dSide" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#1e40af" />
              <stop offset="100%" stopColor="#172554" />
            </linearGradient>
            <linearGradient id="f3dPeakFront" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#bbf7d0" />
              <stop offset="50%" stopColor="#22c55e" />
              <stop offset="100%" stopColor="#15803d" />
            </linearGradient>
            <linearGradient id="f3dPeakSide" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#15803d" />
              <stop offset="100%" stopColor="#14532d" />
            </linearGradient>
            <filter id="f3dGlow" x="-40%" y="-40%" width="180%" height="180%">
              <feGaussianBlur stdDeviation="5" result="b" />
              <feMerge>
                <feMergeNode in="b" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
          </defs>

          <line x1="0" y1={groundY} x2={W} y2={groundY}
                stroke="currentColor" strokeOpacity="0.12" strokeWidth="1" />

          {spikes.map((s, i) => (
            <g key={i} filter={s.peak ? 'url(#f3dGlow)' : undefined}>
              <ellipse cx={s.cx} cy={groundY + 5} rx={fw * 0.7} ry="4" fill="#0f172a" opacity="0.12" />
              <path d={s.side} fill={s.peak ? 'url(#f3dPeakSide)' : 'url(#f3dSide)'} />
              <path d={s.front} fill={s.peak ? 'url(#f3dPeakFront)' : 'url(#f3dFront)'} />
            </g>
          ))}
        </svg>

        <div style={{ display: 'grid', gridTemplateColumns: `repeat(${n}, 1fr)`, marginTop: 8 }}>
          {days.map((d, i) => (
            <div key={i} style={{ textAlign: 'center' }}>
              <div className="mono" style={{ fontSize: 12, fontWeight: 700, color: i === maxIdx ? '#16a34a' : undefined }}>
                {money(d.projectedRevenueUzs)}
              </div>
              <div style={{ fontSize: 12, fontWeight: 600 }}>{weekdayShort(d.weekday)}</div>
              <div className="faint" style={{ fontSize: 11 }}>{d.date.slice(5)}</div>
            </div>
          ))}
        </div>

        <div className="forecast-meta faint" style={{ marginTop: 12, fontSize: 12 }}>
          {t("O'rtacha kunlik")}: {money(data.meanDailyRevenue)} so'm · {data.meanDailySalesCount} {t('savdo')}
        </div>
      </div>
    </div>
  );
}

function weekdayShort(w) {
  return ({
    MONDAY: 'Du', TUESDAY: 'Se', WEDNESDAY: 'Ch', THURSDAY: 'Pa',
    FRIDAY: 'Ju', SATURDAY: 'Sh', SUNDAY: 'Ya',
  })[w] || w.slice(0, 2);
}
