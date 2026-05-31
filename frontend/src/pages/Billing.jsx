import { useState } from 'react';
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

const STATUS_LABEL = { PENDING: 'Kutilmoqda', PAID: "To'langan", FAILED: 'Xato' };

// Display catalogue for the paid tiers. The backend is the source of truth for
// the actual charge — the checkout response carries the real amountUzs.
const PAID_PLANS = [
  { key: 'BASIC', priceUzs: 99000, maxUsers: 5, maxShops: 1 },
  { key: 'STANDARD', priceUzs: 249000, maxUsers: 15, maxShops: 3 },
  { key: 'PRO', priceUzs: 599000, maxUsers: 100, maxShops: 20 },
];

function money(n) {
  return Number(n || 0).toLocaleString('ru-RU');
}

/**
 * In-app billing / subscription (owner-only): plan + trial countdown + user
 * usage, a plan picker that starts a checkout, and payment history. The real
 * Payme/Click hosted checkout plugs into the checkout response later — for now
 * choosing a plan creates a PENDING payment the operator/PSP confirms.
 */
export function Billing() {
  const t = useT();
  const { data, loading, error, reload } = useApi(() => BillingApi.status(), []);
  const { data: payments, reload: reloadPayments } =
    useApi(() => BillingApi.payments().catch(() => []), []);
  const [busyPlan, setBusyPlan] = useState(null);
  const [notice, setNotice] = useState('');

  if (loading) return <Spinner />;
  if (error) {
    return <div className="card" style={{ margin: 16 }}>⚠️ {error}</div>;
  }

  const s = data || {};
  const isTrial = s.plan === 'TRIAL';
  const soon = !s.expired && s.daysRemaining <= 3;
  const userPct = s.maxUsers ? Math.min(100, Math.round((s.currentUsers / s.maxUsers) * 100)) : 0;
  const statusColor = s.expired ? '#dc2626' : soon ? '#d97706' : '#16a34a';
  const statusText = s.expired ? t('Muddati tugagan') : `${s.daysRemaining} ${t('kun qoldi')}`;

  const choose = async (plan) => {
    setBusyPlan(plan);
    setNotice('');
    try {
      const r = await BillingApi.checkout(plan);
      setNotice(`✅ ${t("To'lov yaratildi")} #${r.paymentId} — ${money(r.amountUzs)} UZS. `
        + t("To'lov tizimi (Payme/Click) ulanganda shu yerda davom etadi."));
      reloadPayments();
      reload();
    } catch (err) {
      setNotice('⚠️ ' + (err.message || 'Xatolik'));
    } finally {
      setBusyPlan(null);
    }
  };

  return (
    <div className="page" style={{ padding: 16, maxWidth: 880 }}>
      <h1 style={{ margin: '0 0 4px' }}>{t("Tarif va to'lov")}</h1>
      <p className="muted" style={{ marginTop: 0 }}>
        {t('Obuna holati, foydalanuvchilar limiti va tarifni yangilash')}
      </p>

      {(s.expired || soon) && (
        <div style={{
          margin: '12px 0', padding: '12px 16px', borderRadius: 10,
          background: s.expired ? '#fee2e2' : '#fef3c7',
          color: s.expired ? '#991b1b' : '#92400e', fontWeight: 600,
        }}>
          {s.expired
            ? t('Obuna muddati tugadi — faqat o\'qish rejimi. Ishni davom ettirish uchun tarifni yangilang.')
            : `${t('Sinov muddati tugashiga')} ${s.daysRemaining} ${t('kun qoldi.')}`}
        </div>
      )}

      <div className="card" style={{ padding: 20, display: 'grid', gap: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <div className="muted" style={{ fontSize: 13 }}>{t('Joriy tarif')}</div>
            <div style={{ fontSize: 22, fontWeight: 700 }}>{PLAN_LABEL[s.plan] || s.plan}</div>
          </div>
          <div style={{ textAlign: 'right' }}>
            <div className="muted" style={{ fontSize: 13 }}>
              {isTrial ? t('Sinov tugaydi') : t('Amal qiladi')}
            </div>
            <div style={{ fontSize: 16, fontWeight: 700 }}>{s.subscriptionExpires || '—'}</div>
            <div style={{ color: statusColor, fontWeight: 700, fontSize: 13 }}>{statusText}</div>
          </div>
        </div>
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
            <span className="muted">{t('Foydalanuvchilar')}</span>
            <span style={{ fontWeight: 600 }}>{s.currentUsers} / {s.maxUsers}</span>
          </div>
          <div style={{ height: 8, borderRadius: 6, background: '#e5e7eb', overflow: 'hidden' }}>
            <div style={{
              width: `${userPct}%`, height: '100%',
              background: userPct >= 100 ? '#dc2626' : 'var(--brand-primary, #3b82f6)',
            }} />
          </div>
        </div>
        <div className="muted">{t("Do'konlar limiti")}: {s.maxShops}</div>
      </div>

      {IS_WEB && (
        <div style={{ marginTop: 20 }}>
          <h3 style={{ marginBottom: 8 }}>{t('Tarifni tanlang')}</h3>
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: 12,
          }}>
            {PAID_PLANS.map((p) => (
              <div key={p.key} className="card" style={{
                padding: 16,
                border: s.plan === p.key ? '2px solid var(--brand-primary, #3b82f6)' : undefined,
              }}>
                <div style={{ fontWeight: 700, fontSize: 18 }}>{PLAN_LABEL[p.key]}</div>
                <div style={{ fontSize: 22, fontWeight: 800, margin: '8px 0' }}>
                  {money(p.priceUzs)} <span style={{ fontSize: 13, fontWeight: 400 }}>UZS/oy</span>
                </div>
                <div className="muted" style={{ fontSize: 13 }}>
                  {p.maxUsers} {t('foydalanuvchi')} · {p.maxShops} {t("do'kon")}
                </div>
                <button
                  className="btn btn-primary"
                  style={{ marginTop: 12, width: '100%' }}
                  disabled={busyPlan === p.key}
                  onClick={() => choose(p.key)}
                >
                  {busyPlan === p.key ? '...' : s.plan === p.key ? t('Uzaytirish') : t('Tanlash')}
                </button>
              </div>
            ))}
          </div>
          {notice && <div className="card" style={{ marginTop: 12, padding: 12 }}>{notice}</div>}
        </div>
      )}

      <div className="card" style={{ padding: 20, marginTop: 20 }}>
        <h3 style={{ marginTop: 0 }}>{t("To'lovlar tarixi")}</h3>
        {!payments || payments.length === 0 ? (
          <p className="muted">{t("Hozircha to'lovlar yo'q.")}</p>
        ) : (
          <table style={{ width: '100%', fontSize: 14, borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ textAlign: 'left', color: '#6b7280' }}>
                <th>{t('Sana')}</th>
                <th>{t('Tarif')}</th>
                <th style={{ textAlign: 'right' }}>{t('Summa')}</th>
                <th>{t('Holat')}</th>
              </tr>
            </thead>
            <tbody>
              {payments.map((p) => (
                <tr key={p.id} style={{ borderTop: '1px solid #eee' }}>
                  <td>{(p.createdAt || '').slice(0, 10)}</td>
                  <td>{PLAN_LABEL[p.plan] || p.plan} ({p.months} {t('oy')})</td>
                  <td style={{ textAlign: 'right' }}>{money(p.amountUzs)}</td>
                  <td>{STATUS_LABEL[p.status] || p.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
