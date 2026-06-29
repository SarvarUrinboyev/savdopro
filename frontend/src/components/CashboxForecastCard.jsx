import { AiApi } from '../api/endpoints.js';
import { Loader } from './ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { money } from '../lib/format.js';

/**
 * 7-day cashbox projection, drawn as a clean row of flat premium bars: each
 * day's height is its projected revenue and the busiest day is highlighted in
 * green so the weekly peak reads at a glance. Pure CSS bars (no 3D, no chart
 * lib) that scale to the card width. Data + forecast logic are unchanged.
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
  const revOf = (d) => Number(d.projectedRevenueUzs) || 0;
  const max = days.reduce((m, d) => Math.max(m, revOf(d)), 1);
  let maxIdx = 0;
  days.forEach((d, i) => { if (revOf(d) > revOf(days[maxIdx])) maxIdx = i; });

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
        <p className="faint" style={{ fontSize: 12, marginBottom: 16 }}>
          {t("Oxirgi 30 kun savdosi asosida har bir hafta kuni uchun bashorat (o'rtacha × hafta-kuni koeffitsienti). Taxminiy — real natija farq qilishi mumkin.")}
        </p>

        <div className="fbar-chart" role="img" aria-label={t('Keyingi 7 kun prognozi')}>
          {days.map((d, i) => {
            const pct = Math.max(6, (revOf(d) / max) * 100);
            const peak = i === maxIdx;
            return (
              <div key={i} className={`fbar-col${peak ? ' peak' : ''}`}>
                <div className="fbar-val">{moneyShort(revOf(d))}</div>
                <div className="fbar-track">
                  <span className="fbar-fill" style={{ height: `${pct}%` }} />
                </div>
                <div className="fbar-day">{weekdayShort(d.weekday)}</div>
                <div className="fbar-date faint">{d.date.slice(5)}</div>
              </div>
            );
          })}
        </div>

        <div className="forecast-meta faint" style={{ marginTop: 14, fontSize: 12 }}>
          {t("O'rtacha kunlik")}: {money(data.meanDailyRevenue)} so'm · {data.meanDailySalesCount} {t('savdo')}
        </div>
      </div>
    </div>
  );
}

/** 1 234 567 -> "1.2M" / 45 000 -> "45k" — compact bar labels. */
function moneyShort(value) {
  const n = Number(value) || 0;
  if (n >= 1e6) return `${(n / 1e6).toFixed(1).replace(/\.0$/, '')}M`;
  if (n >= 1e3) return `${Math.round(n / 1e3)}k`;
  return String(Math.round(n));
}

function weekdayShort(w) {
  return ({
    MONDAY: 'Du', TUESDAY: 'Se', WEDNESDAY: 'Ch', THURSDAY: 'Pa',
    FRIDAY: 'Ju', SATURDAY: 'Sh', SUNDAY: 'Ya',
  })[w] || w.slice(0, 2);
}
