import { Navigate, Route, Routes } from 'react-router-dom';
import { ShiftApi } from './api/endpoints.js';
import { Layout } from './components/Layout.jsx';
import { Spinner } from './components/ui.jsx';
import { useAuth } from './context/Auth.jsx';
import { useApi } from './hooks/useApi.js';
import { Calculator } from './pages/Calculator.jsx';
import { CustomerDetail } from './pages/CustomerDetail.jsx';
import { Customers } from './pages/Customers.jsx';
import { Dashboard } from './pages/Dashboard.jsx';
import { Admin } from './pages/Admin.jsx';
import { Debt } from './pages/Debt.jsx';
import { HomeExpenses } from './pages/HomeExpenses.jsx';
import { Login } from './pages/Login.jsx';
import { Shops } from './pages/Shops.jsx';
import { Management } from './pages/Management.jsx';
import { Orders } from './pages/Orders.jsx';
import { Payments } from './pages/Payments.jsx';
import { ShiftClose } from './pages/ShiftClose.jsx';
import { ShiftHistory } from './pages/ShiftHistory.jsx';
import { ShiftOpen } from './pages/ShiftOpen.jsx';
import { ProductEditor } from './pages/ProductEditor.jsx';
import { SupplierDetail } from './pages/SupplierDetail.jsx';
import { Suppliers } from './pages/Suppliers.jsx';
import { Warehouse } from './pages/Warehouse.jsx';

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
        <Route path="shops" element={<Shops />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Route>
    </Routes>
  );
}
