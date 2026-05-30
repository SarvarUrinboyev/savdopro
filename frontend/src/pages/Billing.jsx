import { BillingApi } from '../api/endpoints.js';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { Spinner } from '../components/ui.jsx';
import { IS_WEB } from '../config.js';

const PLAN_LABEL = {
  TRIAL: 'Sinov (Trial)',
  BASIC: "Boshlang'ich",
  STANDARD: 'Standart',
  PRO: 'Pro',
};

function money(n) {
  return Number(n || 0).toLocaleString('ru-RU');
}

/**
 * In-app billing / subscription status (owner-only). Shows the current plan,
 * the trial countdown, user-limit usage and an upgrade entry point. The
 * actual payment flow (Payme/Click) lands in the next step — the upgrade
 * button is a placeholder until then.
 */
export function Billing() {
  const t = useT();
  const { data, loading, error } = useApi(() => BillingApi.status(), []);

  if (loading) return <Spinner />;
  if (error) {
    return <div className="card" style={{ margin: 16 }}>⚠️ {error}</div>;
  }

  const s = data || {};
  const isTrial = s.plan === 'TRIAL';
  const soon = !s.expired && s.daysRemaining <= 3;
  const userPct = s.maxUsers ? Math.min(100, Math.round((s.currentUsers / s.maxUsers) * 100)) : 0;

  const statusColor = s.expired ? '#dc2626' : soon ? '#d97706' : '#16a34a';
  const statusText = s.expired
    ? t('Muddati tugagan')
    : `${s.daysRemaining} ${t('kun qoldi')}`;

  return (
    <div className="page" style={{ padding: 16, maxWidth: 760 }}>
      <h1 style={{ margin: '0 0 4px' }}>{t('Tarif va to\'lov')}</h1>
      <p className="muted" style={{ marginTop: 0 }}>
        {t('Obuna holati, foydalanuvchilar limiti va tarifni yangilash')}
      </p>

      {(s.expired || soon) && (
        <div
          style={{
            margin: '12px 0',
            padding: '12px 16px',
            borderRadius: 10,
            background: s.expired ? '#fee2e2' : '#fef3c7',
            color: s.expired ? '#991b1b' : '#92400e',
            fontWeight: 600,
          }}
        >
          {s.expired
            ? t('Obuna muddati tugadi. Ishni davom ettirish uchun tarifni yangilang.')
            : `${t('Sinov muddati tugashiga')} ${s.daysRemaining} ${t('kun qoldi.')}`}
        </div>
      )}

      <div className="card" style={{ padding: 20, display: 'grid', gap: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <div className="muted" style={{ fontSize: 13 }}>{t('Joriy tarif')}</div>
            <div style={{ fontSize: 22, fontWeight: 700 }}>
              {PLAN_LABEL[s.plan] || s.plan}
            </div>
          </div>
          <div style={{ textAlign: 'right' }}>
            <div className="muted" style={{ fontSize: 13 }}>{t('Oylik narx')}</div>
            <div style={{ fontSize: 20, fontWeight: 700 }}>
              {s.monthlyPriceUzs > 0 ? `${money(s.monthlyPriceUzs)} UZS` : t('Bepul')}
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <span className="muted">
            {isTrial ? t('Sinov tugaydi') : t('Obuna amal qiladi')}: {s.subscriptionExpires || '—'}
          </span>
          <span style={{ color: statusColor, fontWeight: 700 }}>{statusText}</span>
        </div>

        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
            <span className="muted">{t('Foydalanuvchilar')}</span>
            <span style={{ fontWeight: 600 }}>{s.currentUsers} / {s.maxUsers}</span>
          </div>
          <div style={{ height: 8, borderRadius: 6, background: '#e5e7eb', overflow: 'hidden' }}>
            <div
              style={{
                width: `${userPct}%`,
                height: '100%',
                background: userPct >= 100 ? '#dc2626' : 'var(--brand-primary, #3b82f6)',
              }}
            />
          </div>
        </div>

        <div className="muted">{t('Do\'konlar limiti')}: {s.maxShops}</div>

        {IS_WEB && (
          <button
            className="btn btn-primary"
            onClick={() => alert(t('To\'lov tizimi (Payme/Click) tez orada ulanadi.'))}
          >
            {isTrial || s.expired ? t('Tarifni tanlash / to\'lash') : t('Tarifni o\'zgartirish')}
          </button>
        )}
      </div>

      <div className="card" style={{ padding: 20, marginTop: 16 }}>
        <h3 style={{ marginTop: 0 }}>{t('To\'lovlar tarixi')}</h3>
        <p className="muted">{t('To\'lov tizimi ulangach bu yerda ko\'rinadi.')}</p>
      </div>
    </div>
  );
}
