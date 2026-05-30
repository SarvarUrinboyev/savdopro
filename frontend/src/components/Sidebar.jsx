import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/Auth.jsx';
import { useT } from '../context/Settings.jsx';
import { isModuleEnabled } from '../lib/modules.js';

/**
 * Line icons (24x24 stroke), keyed by route. They use currentColor, so
 * they take the nav link's colour automatically (incl. the active state).
 */
const ICON = {
  '/dashboard': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
      <rect x="3" y="3" width="7" height="9" rx="1.5" />
      <rect x="14" y="3" width="7" height="5" rx="1.5" />
      <rect x="14" y="12" width="7" height="9" rx="1.5" />
      <rect x="3" y="16" width="7" height="5" rx="1.5" />
    </svg>
  ),
  '/management': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  ),
  '/expenses': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  ),
  '/home-expenses': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
    </svg>
  ),
  '/payments': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <rect x="2" y="5" width="20" height="14" rx="2.5" />
      <line x1="2" y1="10" x2="22" y2="10" />
      <circle cx="17.5" cy="14.5" r="1.2" />
    </svg>
  ),
  '/orders': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
    </svg>
  ),
  '/warehouse': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
    </svg>
  ),
  '/customers': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  ),
  '/suppliers': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M16 3h5v5M21 3l-7 7M8 21H3v-5M3 21l7-7" />
      <path d="M3 8V5a2 2 0 012-2h3M21 16v3a2 2 0 01-2 2h-3" />
    </svg>
  ),
  '/debt': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
    </svg>
  ),
  '/calculator': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <rect x="4" y="3" width="16" height="18" rx="2" />
      <line x1="8" y1="7" x2="16" y2="7" />
      <line x1="8" y1="12" x2="8" y2="12" />
      <line x1="12" y1="12" x2="12" y2="12" />
      <line x1="16" y1="12" x2="16" y2="12" />
      <line x1="8" y1="16" x2="8" y2="16" />
      <line x1="12" y1="16" x2="12" y2="16" />
      <line x1="16" y1="16" x2="16" y2="16" />
    </svg>
  ),
  '/shift-history': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  ),
  '/shift-close': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M9 5H7a2 2 0 00-2 2v10a2 2 0 002 2h2m6-4l3-3m0 0l-3-3m3 3H9" />
    </svg>
  ),
  '/admin': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 2l8 4v6c0 5-3.5 9-8 10-4.5-1-8-5-8-10V6l8-4z" />
      <path d="M9 12l2 2 4-4" />
    </svg>
  ),
  '/shops': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 9l2-5h14l2 5M3 9v11h18V9M3 9h18M9 15h6" />
    </svg>
  ),
  '/transfers': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M7 7H17M17 7l-3-3M17 7l-3 3" />
      <path d="M17 17H7M7 17l3 3M7 17l3-3" />
    </svg>
  ),
  '/reports': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M18 20V10M12 20V4M6 20v-6"/>
    </svg>
  ),
  '/pos': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="6" width="18" height="13" rx="2" />
      <path d="M3 10h18M7 14h2M11 14h2" />
    </svg>
  ),
  '/pos/history': (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
         strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 12a9 9 0 109-9M3 12l3-3M3 12l3 3M12 7v5l3 2" />
    </svg>
  ),
};

// Each item carries the module `key` used in accounts.enabled_modules.
// The Sidebar filters out items whose key is not in the current user's
// allow-list (server-issued via /me; null = "all visible").
const NAV_ITEMS = [
  { key: 'dashboard',     to: '/dashboard',     label: 'Boshqaruv' },
  { key: 'pos',           to: '/pos',           label: 'Kassa (POS)' },
  { key: 'pos-history',   to: '/pos/history',   label: 'Sotuvlar tarixi' },
  { key: 'promos',        to: '/promos',        label: 'Aksiyalar' },
  { key: 'management',    to: '/management',    label: 'Menejment' },
  { key: 'home-expenses', to: '/home-expenses', label: "Do'kon xarajatlari" },
  { key: 'payments',      to: '/payments',      label: "To'lov" },
  { key: 'orders',        to: '/orders',        label: 'Buyurtmalar' },
  { key: 'warehouse',     to: '/warehouse',     label: 'Ombor' },
  { key: 'customers',     to: '/customers',     label: 'Mijozlar' },
  { key: 'suppliers',     to: '/suppliers',     label: 'Yetkazib beruvchilar' },
  { key: 'debt',          to: '/debt',          label: 'Qarz' },
  { key: 'calculator',    to: '/calculator',    label: 'Kalkulyator' },
  { key: 'shift-history', to: '/shift-history', label: 'Smena tarixi' },
  { key: 'shift-close',   to: '/shift-close',   label: 'Smena yopish' },
  { key: 'reports',       to: '/reports',       label: 'Hisobotlar' },
];

export function Sidebar({ shift, open }) {
  const t = useT();
  const { user, logout } = useAuth();
  const shiftOpen = Boolean(shift);
  const initials = (user?.fullName || user?.username || '?')
    .split(/\s+/).filter(Boolean).slice(0, 2)
    .map((s) => s[0].toUpperCase()).join('') || '?';
  // SUPER_ADMIN always sees everything (modules == null from server).
  // For any other role, the server passes the account's CSV allow-list.
  const moduleCsv = user?.enabledModules ?? null;
  const isOn = (key) => isModuleEnabled(moduleCsv, key);
  return (
    <aside className={`sidebar${open ? ' show' : ''}`}>
      <div className="sidebar-brand">
        <div className="logo">
          <svg viewBox="0 0 40 40" aria-hidden="true">
            <defs>
              <linearGradient id="brandGrad" x1="0" y1="1" x2="1" y2="0">
                <stop offset="0%" stopColor="#1e3a8a" />
                <stop offset="100%" stopColor="#3b82f6" />
              </linearGradient>
            </defs>
            <rect width="40" height="40" rx="9" fill="url(#brandGrad)" />
            {/* Three bars — ascending, the tallest in the brand-green accent. */}
            <rect x="9" y="23.5" width="6.25" height="8.3" rx="1.7"
                  fill="#ffffff" opacity="0.42" />
            <rect x="16.9" y="19" width="6.25" height="12.65" rx="1.7"
                  fill="#ffffff" opacity="0.62" />
            <rect x="24.7" y="13.6" width="6.25" height="18.1" rx="1.7" fill="#22c55e" />
            {/* Trend line with arrow head — sales-growth motif. */}
            <path d="M12.2 20.5 L20 15.8 L28.1 10.9" fill="none"
                  stroke="#ffffff" strokeWidth="2"
                  strokeLinecap="round" strokeLinejoin="round" />
            <path d="M24.7 10.9 L28.1 10.9 L28.1 14.4" fill="none"
                  stroke="#ffffff" strokeWidth="2"
                  strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </div>
        <div>
          <div className="name">
            SavdoPRO <span className="brand-tag">POS</span>
          </div>
          <div className="sub">Avtomatlashtirish</div>
        </div>
      </div>
      <nav className="sidebar-nav">
        {user?.role === 'SUPER_ADMIN' && (
          <NavLink
            to="/admin"
            className={({ isActive }) => `nav-link nav-link-admin ${isActive ? 'active' : ''}`}
          >
            <span className="ico">{ICON['/admin']}</span>
            <span>{t('Super-admin')}</span>
          </NavLink>
        )}
        {(user?.role === 'ACCOUNT_OWNER' || user?.role === 'SUPER_ADMIN') && isOn('shops') && (
          <NavLink
            to="/shops"
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
          >
            <span className="ico">{ICON['/shops']}</span>
            <span>{t("Do'konlar")}</span>
          </NavLink>
        )}
        {(user?.role === 'ACCOUNT_OWNER' || user?.role === 'SUPER_ADMIN') && isOn('transfers') && (
          <NavLink
            to="/transfers"
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
          >
            <span className="ico">{ICON['/transfers']}</span>
            <span>{t("Tovar transferi")}</span>
          </NavLink>
        )}
        {(user?.role === 'ACCOUNT_OWNER' || user?.role === 'SUPER_ADMIN') && (
          <NavLink
            to="/billing"
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
          >
            <span className="ico">💳</span>
            <span>{t("Tarif va to'lov")}</span>
          </NavLink>
        )}
        {NAV_ITEMS.filter((item) => isOn(item.key)).map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
          >
            <span className="ico">{ICON[item.to]}</span>
            <span>{t(item.label)}</span>
          </NavLink>
        ))}
      </nav>
      <div className="sidebar-foot">
        <div className="operator-pill">
          <div className="op-avatar">{initials}</div>
          <div className="op-info">
            <div className="op-role" title={user?.accountName || ''}>
              {user?.fullName || user?.username || t('Operator')}
            </div>
            <div className={`op-status ${shiftOpen ? 'on' : 'off'}`}>
              <span className="op-dot" />
              {shiftOpen ? t('Ochiq') : t('Yopiq')}
            </div>
          </div>
          <button
            className="op-logout"
            onClick={logout}
            title={t('Chiqish')}
            aria-label={t('Chiqish')}
          >
            ⎋
          </button>
        </div>
      </div>
    </aside>
  );
}
