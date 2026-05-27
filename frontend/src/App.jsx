import { lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { ShiftApi } from './api/endpoints.js';
import { Layout } from './components/Layout.jsx';
import { Spinner } from './components/ui.jsx';
import { useAuth } from './context/Auth.jsx';
import { useApi } from './hooks/useApi.js';
import { Login } from './pages/Login.jsx';
import { ShiftOpen } from './pages/ShiftOpen.jsx';

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
const ShiftClose = lazyPage(() => import('./pages/ShiftClose.jsx'), 'ShiftClose');
const ShiftHistory = lazyPage(() => import('./pages/ShiftHistory.jsx'), 'ShiftHistory');
const ProductEditor = lazyPage(() => import('./pages/ProductEditor.jsx'), 'ProductEditor');
const SupplierDetail = lazyPage(() => import('./pages/SupplierDetail.jsx'), 'SupplierDetail');
const Suppliers = lazyPage(() => import('./pages/Suppliers.jsx'), 'Suppliers');
const Transfers = lazyPage(() => import('./pages/Transfers.jsx'), 'Transfers');
const Warehouse = lazyPage(() => import('./pages/Warehouse.jsx'), 'Warehouse');
const Reports = lazyPage(() => import('./pages/Reports.jsx'), 'Reports');

/**
 * Top level: until a shift is open the app shows the "open shift" gate;
 * once open it renders the dashboard and its pages.
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
    return <Login />;
  }

  return <Authenticated />;
}

function Authenticated() {
  const auth = useAuth();
  const { data: shift, loading, error, reload } = useApi(() => ShiftApi.current(), []);

  if (loading) {
    return (
      <div className="center-screen">
        <Spinner />
      </div>
    );
  }

  if (error) {
    // Auth-related failures: bounce back to login. The api client has
    // already cleared the JWT, so logging out completes the reset and
    // App.jsx will render <Login /> on the next render.
    const isAuth = /\b(401|403)\b/.test(String(error))
      || /sessiya|akkaunt/i.test(String(error));
    if (isAuth) {
      auth.logout();
      return null;
    }
    return (
      <div className="center-screen">
        <div className="empty">
          <div className="e-ico">⚠️</div>
          <div className="e-text">{error}</div>
          <button className="btn btn-primary mt-16" onClick={reload}>
            Qayta urinish
          </button>
        </div>
      </div>
    );
  }

  if (!shift) {
    return <ShiftOpen onOpened={reload} />;
  }

  return (
    <Routes>
      <Route element={<Layout shift={shift} />}>
        <Route index element={<Dashboard />} />
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="management" element={<Management />} />
        <Route path="expenses" element={<Navigate to="/home-expenses" replace />} />
        <Route path="home-expenses" element={<HomeExpenses />} />
        <Route path="payments" element={<Payments />} />
        <Route path="orders" element={<Orders />} />
        <Route path="warehouse" element={<Warehouse />} />
        <Route path="warehouse/new" element={<ProductEditor />} />
        <Route path="warehouse/:id" element={<ProductEditor />} />
        <Route path="customers" element={<Customers />} />
        <Route path="customers/:id" element={<CustomerDetail />} />
        <Route path="suppliers" element={<Suppliers />} />
        <Route path="suppliers/:id" element={<SupplierDetail />} />
        <Route path="debt" element={<Debt />} />
        <Route path="calculator" element={<Calculator />} />
        <Route path="shift-history" element={<ShiftHistory />} />
        <Route path="shift-close" element={<ShiftClose onClosed={reload} />} />
        <Route path="admin" element={<Admin />} />
        <Route path="admin/accounts/:id" element={<AccountDetail />} />
        <Route path="admin/audit" element={<AuditLog />} />
        <Route path="shops" element={<Shops />} />
        <Route path="transfers" element={<Transfers />} />
        <Route path="reports" element={<Reports />} />
        <Route path="pos" element={<Pos />} />
        <Route path="pos/history" element={<PosHistory />} />
        <Route path="promos" element={<Promos />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Route>
    </Routes>
  );
}
