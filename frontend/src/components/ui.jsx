import { formatMoney, money, PAYMENT_LABELS, usd } from '../lib/format.js';
import { useT } from '../context/Settings.jsx';

/** Spinning loader. */
export function Spinner() {
  return <div className="spinner" />;
}

/** Wraps page content: spinner while loading, message on error, else children. */
export function Loader({ loading, error, onRetry, children }) {
  const t = useT();
  if (loading) {
    return <Spinner />;
  }
  if (error) {
    return (
      <div className="empty">
        <div className="e-ico">⚠️</div>
        <div className="e-text">{error}</div>
        {onRetry && (
          <button className="btn btn-ghost btn-sm mt-16" onClick={onRetry}>
            {t('Qayta urinish')}
          </button>
        )}
      </div>
    );
  }
  return children;
}

export function EmptyState({ icon = '📭', text, hint, action }) {
  return (
    <div className="empty">
      <div className="e-ico">{icon}</div>
      <div className="e-text">{text}</div>
      {hint && <div className="e-hint">{hint}</div>}
      {action && <div className="e-action">{action}</div>}
    </div>
  );
}

export function PageHeader({ title, desc, children }) {
  return (
    <div className="page-head">
      <div>
        <h1>{title}</h1>
        {desc && <div className="desc">{desc}</div>}
      </div>
      {children && <div className="actions">{children}</div>}
    </div>
  );
}

const PAYMENT_CLASS = {
  NAQD: 'naqd',
  KASSA: 'kassa',
  KARTA: 'karta',
  ARALASH: 'aralash',
  QARZGA: 'qarzga',
};

export function PaymentBadge({ type }) {
  const t = useT();
  return (
    <span className={`badge badge-${PAYMENT_CLASS[type] || 'muted'}`}>
      {PAYMENT_LABELS[type] ? t(PAYMENT_LABELS[type]) : type}
    </span>
  );
}

/** Progress bar coloured by completion: red / amber / green / done. */
export function ProgressBar({ percent }) {
  const value = Math.max(0, Math.min(100, Math.round(percent || 0)));
  let tone = 'pg-red';
  if (value >= 100) {
    tone = 'pg-done';
  } else if (value >= 61) {
    tone = 'pg-green';
  } else if (value >= 31) {
    tone = 'pg-amber';
  }
  return (
    <div className={`progress ${tone}`}>
      <span style={{ width: `${value}%` }} />
    </div>
  );
}

/**
 * Metric tile. By default the value is shown as USD; pass {@code currencyCode}
 * ('USD' / 'UZS') for currency-aware formatting, or {@code currency={false}}
 * for a plain number.
 */
export function MetricCard({
  tone, icon, label, value, sub, currency = true, currencyCode, tag, filled,
}) {
  let display;
  if (currencyCode) {
    display = formatMoney(value, currencyCode);
  } else if (currency) {
    display = usd(value);
  } else {
    display = money(value);
  }
  const cls = `metric tone-${tone}${filled ? ' metric-filled' : ''}`;
  return (
    <div className={cls}>
      {tag && <span className="m-tag">{tag}</span>}
      <div className="m-ico">{icon}</div>
      <div>
        <div className="m-label">{label}</div>
        <div className="m-value">{display}</div>
        {sub && <div className="m-sub">{sub}</div>}
      </div>
    </div>
  );
}

/** A small USD / so'm segmented toggle for a page's display currency. */
export function CurrencyToggle({ value, onChange }) {
  return (
    <div className="chip-row" title="Ko'rinish valyutasi">
      <button
        type="button"
        className={`chip ${value === 'UZS' ? 'active' : ''}`}
        onClick={() => onChange('UZS')}
      >
        so'm
      </button>
      <button
        type="button"
        className={`chip ${value === 'USD' ? 'active' : ''}`}
        onClick={() => onChange('USD')}
      >
        USD
      </button>
    </div>
  );
}
