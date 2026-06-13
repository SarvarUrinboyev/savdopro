import { lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout.jsx';
import { Spinner } from './components/ui.jsx';
import { useAuth } from './context/Auth.jsx';
import { Login } from './pages/Login.jsx';
import { Register } from './pages/Register.jsx';
import { XCallback } from './pages/XCallback.jsx';
import { ForgotPassword } from './pages/ForgotPassword.jsx';
import { Landing } from './pages/Landing.jsx';
import { Help } from './pages/Help.jsx';
import { Stocktake } from './pages/Stocktake.jsx';
import { IS_WEB } from './config.js';
import { isModuleEnabled } from './lib/modules.js';

// Route-level pages are lazy-loaded so each renders only when first navigated
// to, keeping the initial bundle small. The Suspense boundary lives inside
// Layout, around <Outlet />, so the sidebar/topbar stay mounted while a page
// chunk is fetched.
const lazyPage = (loader, exportName) =>
  lazy(() => loader().then((m) => ({ default: m[exportName] })));

const Calculator = lazyPage(() => import('./pages/Calculator.jsx'), 'Calculator');
const CustomerDetail = lazyPage(() => import('./pages/CustomerDetail.jsx'), 'CustomerDetail');
const Customers = lazyPage(() => import('./pages/Customers.jsx'), 'Customers');
const Dashboard = lazyPage(() => import('./pages/Dashboard.jsx'), 'Dashboard');
const Admin = lazyPage(() => import('./pages/Admin.jsx'), 'Admin');
const AccountDetail = lazyPage(() => import('./pages/AccountDetail.jsx'), 'AccountDetail');
const AuditLog = lazyPage(() => import('./pages/AuditLog.jsx'), 'AuditLog');
const Debt = lazyPage(() => import('./pages/Debt.jsx'), 'Debt');
const HomeExpenses = lazyPage(() => import('./pages/HomeExpenses.jsx'), 'HomeExpenses');
const Shops = lazyPage(() => import('./pages/Shops.jsx'), 'Shops');
const Management = lazyPage(() => import('./pages/Management.jsx'), 'Management');
const Orders = lazyPage(() => import('./pages/Orders.jsx'), 'Orders');
const Payments = lazyPage(() => import('./pages/Payments.jsx'), 'Payments');
const Pos = lazyPage(() => import('./pages/Pos.jsx'), 'Pos');
const PosHistory = lazyPage(() => import('./pages/PosHistory.jsx'), 'PosHistory');
const Promos = lazyPage(() => import('./pages/Promos.jsx'), 'Promos');
const ProductEditor = lazyPage(() => import('./pages/ProductEditor.jsx'), 'ProductEditor');
const SupplierDetail = lazyPage(() => import('./pages/SupplierDetail.jsx'), 'SupplierDetail');
const Suppliers = lazyPage(() => import('./pages/Suppliers.jsx'), 'Suppliers');
const Transfers = lazyPage(() => import('./pages/Transfers.jsx'), 'Transfers');
const Warehouse = lazyPage(() => import('./pages/Warehouse.jsx'), 'Warehouse');
const Reports = lazyPage(() => import('./pages/Reports.jsx'), 'Reports');
const Billing = lazyPage(() => import('./pages/Billing.jsx'), 'Billing');
const ProfitLoss = lazyPage(() => import('./pages/ProfitLoss.jsx'), 'ProfitLoss');
const BalanceSheet = lazyPage(() => import('./pages/BalanceSheet.jsx'), 'BalanceSheet');
const CashFlow = lazyPage(() => import('./pages/CashFlow.jsx'), 'CashFlow');
const JournalEntries = lazyPage(() => import('./pages/JournalEntries.jsx'), 'JournalEntries');
const ChartOfAccounts = lazyPage(() => import('./pages/ChartOfAccounts.jsx'), 'ChartOfAccounts');
const AccountingPeriods = lazyPage(() => import('./pages/AccountingPeriods.jsx'), 'AccountingPeriods');
const Reconciliation = lazyPage(() => import('./pages/Reconciliation.jsx'), 'Reconciliation');
const PurchaseOrders = lazyPage(() => import('./pages/PurchaseOrders.jsx'), 'PurchaseOrders');
const Integrations = lazyPage(() => import('./pages/Integrations.jsx'), 'Integrations');

const OWNER_ROLES = ['ACCOUNT_OWNER', 'SUPER_ADMIN'];

/**
 * Route-level access guard. Blocks direct URL access (not just menu hiding)
 * by role and/or the account module allow-list, mirroring the Sidebar. The
 * backend permission checks remain the hard gate (a denied API call returns
 * 403); this is the UX layer that keeps unauthorized pages off screen.
 * Denied access redirects to the always-available dashboard.
 */
function Access({ module, roles, children }) {
  const { user } = useAuth();
  const roleOk = !roles || roles.includes(user?.role);
  const moduleOk = !module || isModuleEnabled(user?.enabledModules ?? null, module);
  if (!roleOk || !moduleOk) return <Navigate to="/dashboard" replace />;
  return children;
}

const g = (element, opts) => <Access {...opts}>{element}</Access>;

/**
 * Top level: shows the login routes until authenticated, then the app shell
 * (dashboard and its pages).
 */
export default function App() {
  const auth = useAuth();

  // Auth gate runs first: until the user is logged in, no API call is fired
  // that could leak data. The login form mounts in its place.
  if (auth.loading) {
    return (
      <div className="center-screen">
        <Spinner />
      </div>
    );
  }
  if (!auth.user) {
    // Hosted web build also exposes a public /register route; the desktop
    // build is single-tenant so it only ever shows Login.
    return (
      <Routes>
        {/* Hosted web build: marketing landing at "/" with login/signup CTAs.
            The desktop build skips it (IS_WEB false) and falls straight to
            Login via the catch-all, as before. */}
        {IS_WEB && <Route path="/" element={<Landing />} />}
        {IS_WEB && <Route path="/register" element={<Register />} />}
        {IS_WEB && <Route path="/oauth/x/callback" element={<XCallback />} />}
        {IS_WEB && <Route path="/forgot-password" element={<ForgotPassword />} />}
        <Route path="/login" element={<Login />} />
        <Route path="*" element={<Login />} />
      </Routes>
    );
  }

  return <Authenticated />;
}

function Authenticated() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Dashboard />} />
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="management" element={g(<Management />, { module: 'management' })} />
        <Route path="expenses" element={<Navigate to="/home-expenses" replace />} />
        <Route path="home-expenses" element={g(<HomeExpenses />, { module: 'home-expenses' })} />
        <Route path="payments" element={g(<Payments />, { module: 'payments' })} />
        <Route path="orders" element={g(<Orders />, { module: 'orders' })} />
        <Route path="warehouse" element={g(<Warehouse />, { module: 'warehouse' })} />
        <Route path="stocktake" element={g(<Stocktake />, { module: 'warehouse' })} />
        <Route path="warehouse/new" element={g(<ProductEditor />, { module: 'warehouse' })} />
        <Route path="warehouse/:id" element={g(<ProductEditor />, { module: 'warehouse' })} />
        <Route path="purchase-orders" element={g(<PurchaseOrders />, { module: 'warehouse' })} />
        <Route path="customers" element={g(<Customers />, { module: 'customers' })} />
        <Route path="customers/:id" element={g(<CustomerDetail />, { module: 'customers' })} />
        <Route path="suppliers" element={g(<Suppliers />, { module: 'suppliers' })} />
        <Route path="suppliers/:id" element={g(<SupplierDetail />, { module: 'suppliers' })} />
        <Route path="debt" element={g(<Debt />, { module: 'debt' })} />
        <Route path="calculator" element={g(<Calculator />, { module: 'calculator' })} />
        <Route path="help" element={<Help />} />
        <Route path="admin" element={g(<Admin />, { roles: ['SUPER_ADMIN'] })} />
        <Route path="admin/accounts/:id" element={g(<AccountDetail />, { roles: ['SUPER_ADMIN'] })} />
        <Route path="admin/audit" element={g(<AuditLog />, { roles: ['SUPER_ADMIN'] })} />
        <Route path="shops" element={g(<Shops />, { module: 'shops', roles: OWNER_ROLES })} />
        <Route path="transfers" element={g(<Transfers />, { module: 'transfers', roles: OWNER_ROLES })} />
        <Route path="billing" element={g(<Billing />, { roles: OWNER_ROLES })} />
        <Route path="reports" element={g(<Reports />, { module: 'reports' })} />
        <Route path="pos" element={g(<Pos />, { module: 'pos' })} />
        <Route path="pos/history" element={g(<PosHistory />, { module: 'pos-history' })} />
        <Route path="promos" element={g(<Promos />, { module: 'promos' })} />
        {/* Accounting (Bosh kitob) — owner/finance only. One sidebar entry,
            six tabbed sub-pages. */}
        <Route path="accounting" element={<Navigate to="/accounting/profit-loss" replace />} />
        <Route path="accounting/profit-loss" element={g(<ProfitLoss />, { roles: OWNER_ROLES })} />
        <Route path="accounting/balance-sheet" element={g(<BalanceSheet />, { roles: OWNER_ROLES })} />
        <Route path="accounting/cash-flow" element={g(<CashFlow />, { roles: OWNER_ROLES })} />
        <Route path="accounting/journal" element={g(<JournalEntries />, { roles: OWNER_ROLES })} />
        <Route path="accounting/accounts" element={g(<ChartOfAccounts />, { roles: OWNER_ROLES })} />
        <Route path="accounting/periods" element={g(<AccountingPeriods />, { roles: OWNER_ROLES })} />
        <Route path="accounting/reconciliation" element={g(<Reconciliation />, { roles: OWNER_ROLES })} />
        <Route path="integrations" element={g(<Integrations />, { roles: OWNER_ROLES })} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Route>
    </Routes>
  );
}
