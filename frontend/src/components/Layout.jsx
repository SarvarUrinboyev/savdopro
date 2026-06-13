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
import { formatDate, todayIso } from '../lib/format.js';

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
export function Layout() {
  const { pathname } = useLocation();
  const { theme, toggleTheme, lang, setLang, t } = useSettings();
  const { isConsolidated, shops } = useShop();
  const title = resolveTitle(pathname);
  const isPosKiosk = pathname === '/pos';
  // Mobile off-canvas nav. Closes automatically whenever the route changes.
  const [navOpen, setNavOpen] = useState(false);
  useEffect(() => { setNavOpen(false); }, [pathname]);

  const navigate = useNavigate();
  // Global function-key shortcuts: jump to common pages without using the mouse.
  useKeyboard(useMemo(() => ({
    F2: () => navigate('/warehouse/new'),   // new product
    F3: () => navigate('/warehouse'),       // warehouse
    // On the Kassa screen F4 belongs to the register (To'lov) — don't
    // yank the cashier away to the customers page mid-sale.
    F4: () => { if (!pathname.startsWith('/pos')) navigate('/customers'); },
    F5: () => navigate('/payments'),        // payments
    F10: () => navigate('/reports'),        // reports
  }), [navigate, pathname]));

  return (
    <div className={`app-shell ${isPosKiosk ? 'app-shell-pos-kiosk' : ''}`}>
      {!isPosKiosk && <QuickSearch />}
      {!isPosKiosk && <AiChatWidget />}
      {!isPosKiosk && <Sidebar open={navOpen} />}
      {!isPosKiosk && navOpen && (
        <div className="nav-scrim" onClick={() => setNavOpen(false)} aria-hidden="true" />
      )}
      <div className={`main ${isPosKiosk ? 'main-pos-kiosk' : ''}`}>
        {!isPosKiosk && (
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
          </div>
        </header>
        )}
        {!isPosKiosk && <SubscriptionBanner />}
        {!isPosKiosk && isConsolidated && (
          <div className="consolidated-banner">
            <span className="cb-ico">🌐</span>
            <span>
              <b>{t("Hamma do'konlar rejimi faol")}.</b>{' '}
              {t("Bu yerda barcha")} {shops.length}{' '}
              {t("ta do'konning ma'lumotlari jamlangan. Yangi mahsulot/mijoz/to'lov qo'shish uchun aniq do'konni tanlang.")}
            </span>
          </div>
        )}
        <main className={`content ${isPosKiosk ? 'content-pos-kiosk' : ''}`}>
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
