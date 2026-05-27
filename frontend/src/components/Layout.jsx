import { Suspense, useMemo } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { AiChatWidget } from './AiChatWidget.jsx';
import { QuickSearch } from './QuickSearch.jsx';
import { Sidebar } from './Sidebar.jsx';
import { ShopSwitcher } from './ShopSwitcher.jsx';
import { ErrorBoundary } from './ErrorBoundary.jsx';
import { Spinner } from './ui.jsx';
import { useKeyboard } from '../hooks/useKeyboard.js';
import { useAuth } from '../context/Auth.jsx';
import { useSettings } from '../context/Settings.jsx';
import { useShop } from '../context/Shop.jsx';
import { LANGUAGES } from '../i18n/i18n.js';
import { formatDate, formatTime, todayIso } from '../lib/format.js';

/** Warn 4 days before the subscription cuts off. */
const WARNING_DAYS = 4;

const PAGE_TITLES = {
  '/dashboard': 'Boshqaruv',
  '/pos': 'Kassa (POS)',
  '/pos/history': 'Sotuvlar tarixi',
  '/management': 'Menejment',
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
  const { user } = useAuth();
  const { isConsolidated, shops } = useShop();
  const title = resolveTitle(pathname);
  const showSubWarning = user
    && user.subscriptionExpires
    && user.daysUntilBlock <= WARNING_DAYS
    && user.daysUntilBlock >= 0;

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
      <Sidebar shift={shift} />
      <div className="main">
        <header className="topbar">
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
            {shift ? (
              <span className="shift-pill open">
                <span className="dot" />
                {t('Smena ochiq')} &middot; {formatTime(shift.openedAt)}
              </span>
            ) : (
              <span className="shift-pill closed">
                <span className="dot" />
                {t('Smena yopiq')}
              </span>
            )}
          </div>
        </header>
        {showSubWarning && (
          <div className="sub-warning">
            <span className="sub-warning-ico">⏰</span>
            <span>
              <b>{t('Obuna muddati tugashiga')} {user.daysUntilBlock}{' '}
                {t('kun qoldi')}.</b>{' '}
              {t('To\'lov muddati')}: {user.subscriptionExpires}.{' '}
              {t('To\'lamasangiz akkaunt avtomatik bloklanadi.')}
            </span>
          </div>
        )}
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
