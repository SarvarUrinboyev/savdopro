import { Link } from 'react-router-dom';
import { useAuth } from '../context/Auth.jsx';
import { useT } from '../context/Settings.jsx';
import { formatDate, todayIso } from '../lib/format.js';

/** Warn this many days before the subscription cuts off. */
const WARNING_DAYS = 4;

const OWNER_ROLES = ['ACCOUNT_OWNER', 'SUPER_ADMIN'];

/**
 * Global subscription nudge shown above the page content. Three states,
 * sourced entirely from the cached session (`/api/auth/me`) so there's no
 * extra round-trip on every navigation:
 *
 *  - EXPIRED  → red: the shop backend is read-only (JwtAuthFilter blocks
 *               writes on a lapsed subExp claim), so we explain why writes
 *               fail and steer the owner to renew.
 *  - WARNING  → amber: cut-off is within {@link WARNING_DAYS} days.
 *  - none     → renders nothing (healthy, or no expiry at all).
 *
 * The "renew" CTA only appears for account owners / super-admins, since the
 * billing page is gated to those roles; a cashier just sees the notice.
 */
export function SubscriptionBanner() {
  const { user } = useAuth();
  const t = useT();

  if (!user || !user.subscriptionExpires) return null;

  // ISO dates (yyyy-mm-dd) compare correctly as strings. Mirrors the
  // backend's `expires.isBefore(today)` without any timezone math.
  const expired = user.subscriptionExpires < todayIso();
  const days = typeof user.daysUntilBlock === 'number' ? user.daysUntilBlock : null;
  const warning = !expired && days !== null && days >= 0 && days <= WARNING_DAYS;

  if (!expired && !warning) return null;

  const isOwner = OWNER_ROLES.includes(user.role);
  const when = formatDate(user.subscriptionExpires);

  if (expired) {
    return (
      <div className="sub-banner expired" role="alert">
        <span className="sub-banner-ico">⛔</span>
        <span className="sub-banner-text">
          <b>{t('Obuna muddati tugagan')} ({when}).</b>{' '}
          {t("Tizim faqat o'qish rejimida — yangi sotuv, mahsulot yoki to'lov qo'shib bo'lmaydi.")}
        </span>
        {isOwner && (
          <Link to="/billing" className="sub-banner-cta">
            {t('Tarifni yangilash')} →
          </Link>
        )}
      </div>
    );
  }

  return (
    <div className="sub-banner warn" role="status">
      <span className="sub-banner-ico">⏰</span>
      <span className="sub-banner-text">
        <b>{t('Obuna muddati tugashiga')} {days} {t('kun qoldi')}.</b>{' '}
        {t("To'lov muddati")}: {when}.{' '}
        {!isOwner && t("To'lamasangiz akkaunt o'qish rejimiga o'tadi.")}
      </span>
      {isOwner && (
        <Link to="/billing" className="sub-banner-cta">
          {t('Yangilash')} →
        </Link>
      )}
    </div>
  );
}
