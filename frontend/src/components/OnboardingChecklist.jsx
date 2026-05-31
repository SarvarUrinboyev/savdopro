import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { CustomerApi, PosApi, ProductApi } from '../api/endpoints.js';
import { IS_WEB } from '../config.js';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { useStickyState } from '../hooks/useStickyState.js';

// The first three things a new shop owner should do. Each step is detected
// from real data, so it ticks itself off as the owner actually does it.
const STEPS = [
  {
    key: 'product', icon: '📦', to: '/warehouse/new',
    title: "Birinchi mahsulotni qo'shing",
    text: "Omborga tovar kiriting — sotuv shu yerdan boshlanadi.",
  },
  {
    key: 'sale', icon: '🧾', to: '/pos',
    title: "Birinchi sotuvni rasmiylashtiring",
    text: "Kassada (POS) sinov sotuvini o'tkazib ko'ring.",
  },
  {
    key: 'customer', icon: '👥', to: '/customers',
    title: "Mijozlaringizni qo'shing",
    text: "Doimiy mijozlar va qarz daftarini yuriting.",
  },
];

/**
 * First-run onboarding guide on the dashboard (hosted web build only). Shows a
 * 3-step checklist whose ticks are derived from live data — add a product,
 * make a sale, add a customer. When all three are done (or the owner dismisses
 * it) the card retires itself permanently on that device, so it never nags an
 * established shop. Desktop builds skip it entirely (and never fetch).
 */
export function OnboardingChecklist() {
  const t = useT();
  const [doneFlag, setDoneFlag] = useStickyState('sp-onboarding-done', '');
  const skip = !IS_WEB || doneFlag === '1';

  const { data } = useApi(
    () => (skip ? Promise.resolve(null) : Promise.all([
      ProductApi.list().catch(() => []),
      PosApi.recent(0, 1).catch(() => ({ items: [] })),
      CustomerApi.list().catch(() => []),
    ])),
    [skip],
  );

  const status = {
    product: Array.isArray(data?.[0]) && data[0].length > 0,
    sale: (data?.[1]?.items?.length ?? 0) > 0,
    customer: Array.isArray(data?.[2]) && data[2].length > 0,
  };
  const completed = STEPS.filter((s) => status[s.key]).length;
  const allDone = data != null && completed === STEPS.length;

  // Once every step is satisfied, retire the checklist for good on this device.
  useEffect(() => {
    if (allDone) setDoneFlag('1');
  }, [allDone, setDoneFlag]);

  if (skip || !data || allDone) return null;

  return (
    <div className="card onb section">
      <div className="onb-head">
        <div>
          <div className="onb-title">🚀 {t('Boshlash uchun qadamlar')}</div>
          <div className="onb-sub">
            {t("SavdoPRO'ni 3 qadamda ishga tushiring")} · {completed}/{STEPS.length}
          </div>
        </div>
        <button type="button" className="onb-skip" onClick={() => setDoneFlag('1')}>
          {t('Yopish')}
        </button>
      </div>
      <div className="onb-progress">
        <div
          className="onb-progress-bar"
          style={{ width: `${(completed / STEPS.length) * 100}%` }}
        />
      </div>
      <div className="onb-steps">
        {STEPS.map((s) => {
          const ok = status[s.key];
          return (
            <Link key={s.key} to={s.to} className={`onb-step${ok ? ' done' : ''}`}>
              <span className="onb-check">{ok ? '✓' : s.icon}</span>
              <span className="onb-step-text">
                <span className="onb-step-title">{t(s.title)}</span>
                <span className="onb-step-desc">{t(s.text)}</span>
              </span>
              {!ok && <span className="onb-arrow">→</span>}
            </Link>
          );
        })}
      </div>
    </div>
  );
}
