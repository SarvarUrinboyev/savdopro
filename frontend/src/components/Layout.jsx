import { Suspense, useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { AiChatWidget } from './AiChatWidget.jsx';
import { QuickSearch } from './QuickSearch.jsx';
import { Sidebar } from './Sidebar.jsx';
import { ShopSwitcher } from './ShopSwitcher.jsx';
import { ErrorBoundary } from './ErrorBoundary.jsx';
import { SubscriptionBanner } from './SubscriptionBanner.jsx';
import { Spinner } from './ui.jsx';
import { useKeyboard } from '../hooks/useKeyboard.js';
import { useSettings } from '../context/Settings.jsx';
import { useShop } from '../context/Shop.jsx';
import { LANGUAGES } from '../i18n/i18n.js';
import { formatDate, formatTime, todayIso } from '../lib/format.js';

/** Header shift status pill — click to open a small "ochish / yopish" menu. */
function ShiftPill({ shift, t }) {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const go = (path) => { setOpen(false); navigate(path); };
  return (
    <div style={{ position: 'relative' }}>
      <button
        type="button"
        className={`shift-pill ${shift ? 'open' : 'closed'}`}
        onClick={() => setOpen((v) => !v)}
        title={t('Smena')}
        style={{ cursor: 'pointer', border: 'none', font: 'inherit' }}
      >
        <span className="dot" />
        {shift
          ? `${t('Smena ochiq')} · ${formatTime(shift.openedAt)}`
          : t('Smena yopiq')}
      </button>
      {open && (
        <>
          <div onClick={() => setOpen(false)}
               style={{ position: 'fixed', inset: 0, zIndex: 60 }} />
          <div style={{
            position: 'absolute', right: 0, top: 'calc(100% + 6px)', zIndex: 61,
            background: 'var(--surface)', border: '1px solid var(--border-strong)',
            borderRadius: 12, boxShadow: '0 14px 34px -12px rgba(0,0,0,.55)',
            padding: 6, minWidth: 200, display: 'flex', flexDirection: 'column', gap: 2,
          }}>
            <button className="btn btn-ghost" style={{ justifyContent: 'flex-start' }}
                    onClick={() => go('/shift-open')}>
              🔓 {t('Smena ochish')}
            </button>
            <button className="btn btn-ghost" style={{ justifyContent: 'flex-start' }}
                    onClick={() => go('/shift-close')}>
              🔒 {t('Smena yopish')}
            </button>
          </div>
        </>
      )}
    </div>
  );
}

const PAGE_TITLES = {
  '/dashboard': 'Boshqaruv',
  '/pos': 'Kassa (POS)',
  '/pos/history': 'Sotuvlar tarixi',
  '/management': 'Moliya',
  '/home-expenses': "Do'kon xarajatlari",
  '/payments': "To'lov",
  '/orders': 'Buyurtmalar',
  '/warehouse': 'Ombor',
  '/customers': 'Mijozlar',
  '/suppliers': 'Yetkazib beruvchilar',
  '/debt': 'Qarz',
  '/calculator': 'Kalkulyator',
  '/shift-history': 'Smena tarixi',
  '/shift-close': 'Smena yopish',
  '/admin': 'Super-admin',
  '/shops': "Do'konlar",
  '/reports': 'Hisobotlar',
};

/** Resolves the topbar title, treating nested routes by their prefix. */
function resolveTitle(pathname) {
  if (pathname.startsWith('/warehouse')) {
    return 'Ombor';
  }
  if (pathname.startsWith('/customers')) {
    return 'Mijozlar';
  }
  if (pathname.startsWith('/suppliers')) {
    return 'Yetkazib beruvchilar';
  }
  return PAGE_TITLES[pathname] || 'Boshqaruv';
}

/** App shell: fixed sidebar, sticky topbar and the routed page. */
export function Layout({ shift }) {
  const { pathname } = useLocation();
  const { theme, toggleTheme, lang, setLang, t } = useSettings();
  const { isConsolidated, shops } = useShop();
  const title = resolveTitle(pathname);
  // Mobile off-canvas nav. Closes automatically whenever the route changes.
  const [navOpen, setNavOpen] = useState(false);
  useEffect(() => { setNavOpen(false); }, [pathname]);

  const navigate = useNavigate();
  // Global function-key shortcuts: jump to common pages without using the mouse.
  useKeyboard(useMemo(() => ({
    F2: () => navigate('/warehouse/new'),   // new product
    F3: () => navigate('/warehouse'),       // warehouse
    F4: () => navigate('/customers'),       // customers
    F5: () => navigate('/payments'),        // payments
    F9: () => navigate('/shift-close'),     // close shift
    F10: () => navigate('/reports'),        // reports
  }), [navigate]));

  return (
    <div className="app-shell">
      <QuickSearch />
      <AiChatWidget />
      <Sidebar shift={shift} open={navOpen} />
      {navOpen && (
        <div className="nav-scrim" onClick={() => setNavOpen(false)} aria-hidden="true" />
      )}
      <div className="main">
        <header className="topbar">
          <button
            type="button"
            className="nav-toggle"
            onClick={() => setNavOpen((v) => !v)}
            aria-label={t('Menyu')}
          >
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none"
                 stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <line x1="3" y1="6" x2="21" y2="6" />
              <line x1="3" y1="12" x2="21" y2="12" />
              <line x1="3" y1="18" x2="21" y2="18" />
            </svg>
          </button>
          <div className="breadcrumb">
            <span className="bc-base">{t('Platforma')}</span>
            <span className="bc-sep">/</span>
            <span className="bc-page">
              <span className="bc-dot" />
              {t(title)}
            </span>
          </div>
          <ShopSwitcher />
          <div className="right">
            <select
              className="lang-select"
              value={lang}
              onChange={(e) => setLang(e.target.value)}
              title={t('Til')}
            >
              {LANGUAGES.map((l) => (
                <option key={l.code} value={l.code}>{l.label}</option>
              ))}
            </select>
            <button
              className="icon-btn"
              onClick={toggleTheme}
              title={theme === 'dark' ? t("Yorug' mavzu") : t("Qorong'i mavzu")}
            >
              {theme === 'dark' ? '☀️' : '🌙'}
            </button>
            <span className="date">📅 {formatDate(todayIso())}</span>
            <ShiftPill shift={shift} t={t} />
          </div>
        </header>
        <SubscriptionBanner />
        {isConsolidated && (
          <div className="consolidated-banner">
            <span className="cb-ico">🌐</span>
            <span>
              <b>{t("Hamma do'konlar rejimi faol")}.</b>{' '}
              {t("Bu yerda barcha")} {shops.length}{' '}
              {t("ta do'konning ma'lumotlari jamlangan. Yangi mahsulot/mijoz/to'lov qo'shish uchun aniq do'konni tanlang.")}
            </span>
          </div>
        )}
        <main className="content">
          <ErrorBoundary key={pathname}>
            <Suspense fallback={<div className="center-screen"><Spinner /></div>}>
              <Outlet />
            </Suspense>
          </ErrorBoundary>
        </main>
      </div>
    </div>
  );
}
