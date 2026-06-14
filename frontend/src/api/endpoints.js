// Domain-grouped REST calls.
//
// Auth + Admin endpoints go to the *License Server* (central, port 9090
// in dev / VPS in prod). Everything else goes to the customer's local
// backend on http://127.0.0.1:8086 via the regular `api` client.

import { api, getToken } from './client.js';
import { licenseApi } from './licenseClient.js';

function qs(params) {
  if (!params) return '';
  const pairs = Object.entries(params)
    .filter(([, v]) => v !== null && v !== undefined && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(v)}`);
  return pairs.length ? `?${pairs.join('&')}` : '';
}

export const DashboardApi = {
  today: () => api.get('/dashboard'),
};

export const ExchangeRateApi = {
  get: () => api.get('/exchange-rate'),
};

export const ExpenseApi = {
  list: (params) => api.get('/expenses' + qs(params)),
  create: (body) => api.post('/expenses', body),
  update: (id, body) => api.put(`/expenses/${id}`, body),
  remove: (id) => api.del(`/expenses/${id}`),
  bulkPreview: (body) => api.post('/expenses/bulk-import/preview', body),
  bulkImport: (body) => api.post('/expenses/bulk-import', body),
};

export const HomeExpenseApi = {
  list: (params) => api.get('/home-expenses' + qs(params)),
  create: (body) => api.post('/home-expenses', body),
  update: (id, body) => api.put(`/home-expenses/${id}`, body),
  remove: (id) => api.del(`/home-expenses/${id}`),
  bulkPreview: (body) => api.post('/home-expenses/bulk-import/preview', body),
  bulkImport: (body) => api.post('/home-expenses/bulk-import', body),
};

export const OrderApi = {
  all: () => api.get('/orders'),
  grouped: () => api.get('/orders/grouped'),
  today: () => api.get('/orders/today'),
  create: (body) => api.post('/orders', body),
  update: (id, body) => api.put(`/orders/${id}`, body),
  remove: (id) => api.del(`/orders/${id}`),
  complete: (id, body) => api.patch(`/orders/${id}/complete`, body),
};

export const DebtApi = {
  summary: () => api.get('/debts/summary'),
  myList: () => api.get('/debtors'),
  myCreate: (body) => api.post('/debtors', body),
  myUpdate: (id, body) => api.put(`/debtors/${id}`, body),
  myRemove: (id) => api.del(`/debtors/${id}`),
  myPay: (id, body) => api.patch(`/debtors/${id}/partial-pay`, body),
  myAdd: (id, body) => api.patch(`/debtors/${id}/add-amount`, body),
  myHistory: (id) => api.get(`/debtors/${id}/history`),
  custList: () => api.get('/customer-debts'),
  custCreate: (body) => api.post('/customer-debts', body),
  custUpdate: (id, body) => api.put(`/customer-debts/${id}`, body),
  custRemove: (id) => api.del(`/customer-debts/${id}`),
  custPay: (id, body) => api.patch(`/customer-debts/${id}/partial-pay`, body),
  custAdd: (id, body) => api.patch(`/customer-debts/${id}/add-amount`, body),
  custHistory: (id) => api.get(`/customer-debts/${id}/history`),
};

export const BalanceApi = {
  today: () => api.get('/balance/today'),
  set: (body) => api.post('/balance', body),
};

async function uploadFile(path, file) {
  const form = new FormData();
  form.append('file', file);
  const headers = {};
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const response = await fetch('/api' + path, { method: 'POST', body: form, headers });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new Error(data?.message || data?.detail || `Xatolik (${response.status})`);
  }
  return data;
}

export const AuthApi = {
  login: (body) => licenseApi.post('/api/auth/login', body),
  register: (body) => licenseApi.post('/api/auth/register', body),
  // Signup screen: which verification/social-login features are live.
  signupConfig: () => licenseApi.get('/api/auth/signup/config'),
  // Signup step 1: SMS a phone-verification code.
  signupRequestOtp: (phone) => licenseApi.post('/api/auth/signup/request-otp', { phone }),
  // Social login: exchange a Google access token (from the GIS account chooser)
  // for a SavdoPRO session.
  socialGoogle: (accessToken) => licenseApi.post('/api/auth/social/google', { accessToken }),
  // Social login: a verified Telegram Login Widget payload → SavdoPRO session.
  telegramLogin: (body) => licenseApi.post('/api/auth/telegram', body),
  // Social login: a Facebook JS SDK access token → SavdoPRO session.
  socialFacebook: (accessToken) => licenseApi.post('/api/auth/social/facebook', { accessToken }),
  // Social login: X (Twitter) OAuth2 — the redirect code + PKCE verifier.
  socialX: (code, codeVerifier) => licenseApi.post('/api/auth/social/x', { code, codeVerifier }),
  forgotPassword: (phone) => licenseApi.post('/api/auth/forgot-password', { phone }),
  resetPassword: (body) => licenseApi.post('/api/auth/reset-password', body),
  refresh: (body) => licenseApi.post('/api/auth/refresh', body),
  logout: (body) => licenseApi.post('/api/auth/logout', body),
  me: () => licenseApi.get('/api/auth/me'),
};

export const BillingApi = {
  status: () => licenseApi.get('/api/billing/status'),
  // provider: 'CLICK' | 'PAYME' | undefined (undefined → MANUAL placeholder).
  checkout: (plan, months, provider) =>
    licenseApi.post('/api/billing/checkout', { plan, months, provider }),
  payments: () => licenseApi.get('/api/billing/payments'),
};

export const AuditApi = {
  list: (page = 0, size = 50) =>
    licenseApi.get(`/api/admin/audit${qs({ page, size })}`),
};

export const ShopApi = {
  list: () => api.get('/shops'),
  create: (body) => api.post('/shops', body),
  update: (id, body) => api.put(`/shops/${id}`, body),
  setMain: (id) => api.patch(`/shops/${id}/main`),
  remove: (id) => api.del(`/shops/${id}`),
};

export const AdminApi = {
  listAccounts: () => licenseApi.get('/api/admin/accounts'),
  accountDetail: (id) => licenseApi.get(`/api/admin/accounts/${id}`),
  grant: (id, plan, months) =>
    licenseApi.post(`/api/admin/accounts/${id}/grant`, { plan, months }),
  createAccount: (body) => licenseApi.post('/api/admin/accounts', body),
  updateAccount: (id, body) => licenseApi.put(`/api/admin/accounts/${id}`, body),
  setBlocked: (id, blocked) =>
    licenseApi.patch(`/api/admin/accounts/${id}/block`, { blocked }),
  setModules: (id, enabledModules) =>
    licenseApi.patch(`/api/admin/accounts/${id}/modules`, { enabledModules }),
  deleteAccount: (id) => licenseApi.del(`/api/admin/accounts/${id}`),
  createUser: (accountId, body) =>
    licenseApi.post(`/api/admin/accounts/${accountId}/users`, body),
  resetPassword: (userId, password) =>
    licenseApi.patch(`/api/admin/users/${userId}/password`, { password }),
  setPermissions: (userId, permissions) =>
    licenseApi.patch(`/api/admin/users/${userId}/permissions`, { permissions }),
  deleteUser: (userId) => licenseApi.del(`/api/admin/users/${userId}`),
  auditList: (page = 0, size = 50) => licenseApi.get(`/api/admin/audit?page=${page}&size=${size}`),
};

export const AiApi = {
  // `history` is an optional [{question, answer}, ...] tail so the backend
  // keeps context for follow-up questions ("a o'tgan oychi?").
  ask: (question, history) => api.post('/ai/ask', { question, history }),
  forecast: () => api.get('/ai/forecast'),
  reorderQueue: () => api.get('/ai/reorder-queue'),
  slowMovers: () => api.get('/ai/slow-movers'),
  cashboxForecast: () => api.get('/ai/cashbox-forecast'),
  anomalies: () => api.get('/ai/anomalies'),
  anomalyHistory: (params) => api.get('/ai/anomalies/history' + qs(params)),
  acknowledgeAnomaly: (id) => api.post(`/ai/anomalies/${id}/acknowledge`, {}),
};

// External integrations: API keys + outbound webhooks (owner only).
export const IntegrationsApi = {
  meta: () => api.get('/integrations/meta'),
  listKeys: () => api.get('/integrations/api-keys'),
  createKey: (body) => api.post('/integrations/api-keys', body),
  revokeKey: (id) => api.del(`/integrations/api-keys/${id}`),
  listWebhooks: () => api.get('/integrations/webhooks'),
  createWebhook: (body) => api.post('/integrations/webhooks', body),
  deleteWebhook: (id) => api.del(`/integrations/webhooks/${id}`),
  testWebhook: (id) => api.post(`/integrations/webhooks/${id}/test`, {}),
  deliveries: () => api.get('/integrations/webhooks/deliveries'),
};

export const PromoApi = {
  list: () => api.get('/promos'),
  active: () => api.get('/promos/active'),
  get: (id) => api.get(`/promos/${id}`),
  create: (body) => api.post('/promos', body),
  update: (id, body) => api.put(`/promos/${id}`, body),
  remove: (id) => api.del(`/promos/${id}`),
};

export const PosApi = {
  checkout: (body) => api.post('/pos/checkout', body),
  recent: (page = 0, size = 50) => api.get(`/pos/sales?page=${page}&size=${size}`),
  get: (id) => api.get(`/pos/sales/${id}`),
  refund: (id, body) => api.post(`/pos/sales/${id}/refund`, body ?? { items: [] }),
  // Send/re-send the receipt to the sale's customer. Returns { channel }.
  sendReceipt: (id) => api.post(`/pos/sales/${id}/send-receipt`, {}),
  // Per-cashier performance for [from, to] (yyyy-MM-dd).
  cashierStats: (from, to) => api.get('/pos/cashier-stats' + qs({ from, to })),
};

export const ProductApi = {
  list: (params) => api.get('/products' + qs(params)),
  get: (id) => api.get(`/products/${id}`),
  create: (body) => api.post('/products', body),
  update: (id, body) => api.put(`/products/${id}`, body),
  remove: (id) => api.del(`/products/${id}`),
  adjust: (id, body) => api.patch(`/products/${id}/adjust`, body),
  movements: (id) => api.get(`/products/${id}/movements`),
  stocktake: (body) => api.post('/products/stocktake', body),
  lowStock: () => api.get('/products/low-stock'),
  scan: (body) => api.post('/products/scan', body),
  // Global barcode lookup: a read-only suggestion for an unknown code. The scan
  // modal calls it only as a fallback when the national catalogue has nothing.
  // Returns
  // { found, name, suggestedCategory, source }.
  barcodeLookup: (code) => api.get('/products/barcode-lookup' + qs({ code })),
  importFile: (file) => uploadFile('/products/import', file),
  templateUrl: '/api/products/import/template',
};

export const CategoryApi = {
  list: () => api.get('/categories'),
  create: (body) => api.post('/categories', body),
  remove: (id) => api.del(`/categories/${id}`),
};

export const DeviceApi = {
  // Sold-device (IMEI) tracking. params: { q, status, onlyDebt }.
  list: (params) => api.get('/devices' + qs(params)),
  // Update bookkeeping status: { status: ACTIVE|BLOCKED|RETURNED, note }.
  setStatus: (id, body) => api.patch(`/devices/${id}`, body),
};

export const TerminalApi = {
  today: () => api.get('/terminal/today'),
  history: () => api.get('/terminal/history'),
  save: (body) => api.post('/terminal', body),
};

export const ReportApi = {
  endOfDay: (date) => api.get('/report/end-of-day' + qs({ date })),
  sendTelegram: (date) => api.post('/report/send-telegram' + qs({ date })),
  profitByProduct: (params) => api.get('/report/profit-by-product' + qs(params)),
  hourlySales: (params) => api.get('/report/hourly-sales' + qs(params)),
  // Branded PDF downloads (Phase 4.1). These return raw URLs because
  // the desktop's print pipeline opens them directly — see lib/download.js.
  salesPdfUrl: (params) => '/api/report/pdf/sales' + qs(params),
  inventoryPdfUrl: (shopLabel) =>
    '/api/report/pdf/inventory' + qs({ shopLabel }),
  customerLedgerPdfUrl: (customerId) =>
    `/api/report/pdf/customer/${customerId}/ledger`,
};

export const CustomerApi = {
  list: () => api.get('/customers'),
  detail: (id) => api.get(`/customers/${id}`),
  create: (body) => api.post('/customers', body),
  update: (id, body) => api.put(`/customers/${id}`, body),
  remove: (id) => api.del(`/customers/${id}`),
  addTransaction: (id, body) => api.post(`/customers/${id}/transactions`, body),
  addTransactions: (id, list) => api.post(`/customers/${id}/transactions/batch`, list),
  updateTransaction: (id, txId, body) =>
    api.put(`/customers/${id}/transactions/${txId}`, body),
  removeTransaction: (id, txId) => api.del(`/customers/${id}/transactions/${txId}`),
  // Phase 4.4 loyalty: burn N points → returns updated customer.
  redeemPoints: (id, points) =>
    api.post(`/customers/${id}/loyalty/redeem`, { points }),
  // Notify a customer over the best channel (Telegram bot, else SMS).
  // template: 'DEBT' | 'ORDER_READY' | 'CUSTOM'. Returns { channel }.
  notify: (id, body) => api.post(`/customers/${id}/notify`, body),
  // Remind every customer who owes money. Returns per-channel counts.
  remindDebtors: () => api.post('/customers/remind-debtors', {}),
  // Online-payment context: { debtUsd, suggestedSom, paymeEnabled, clickEnabled }.
  payInfo: (id) => api.get(`/customers/${id}/pay-info`),
  // Generate a Click/Payme checkout link. body: { provider, amountSom } → { url }.
  payLink: (id, body) => api.post(`/customers/${id}/pay-link`, body),
};

export const ManagementApi = {
  summary: (params) => api.get('/management/summary' + qs(params)),
  soldGoods: (params) => api.get('/management/sold-goods' + qs(params)),
  soldGoodsExportUrl: (params) => '/api/management/sold-goods/export' + qs(params),
  createCost: (body) => api.post('/management/costs', body),
  updateCost: (id, body) => api.put(`/management/costs/${id}`, body),
  removeCost: (id) => api.del(`/management/costs/${id}`),
};

// Double-entry accounting core (Bosh kitob). Owner / finance only — gated to
// the MANAGEMENT permission on the backend.
export const AccountingApi = {
  // Chart of accounts
  accounts: () => api.get('/accounting/accounts'),
  createAccount: (body) => api.post('/accounting/accounts', body),
  updateAccount: (id, body) => api.put(`/accounting/accounts/${id}`, body),
  removeAccount: (id) => api.del(`/accounting/accounts/${id}`),
  // Journal
  journal: (params) => api.get('/accounting/journal' + qs(params)),
  journalEntry: (id) => api.get(`/accounting/journal/${id}`),
  createJournal: (body) => api.post('/accounting/journal', body),
  reverseJournal: (id) => api.post(`/accounting/journal/${id}/reverse`, {}),
  removeJournal: (id) => api.del(`/accounting/journal/${id}`),
  // Statements
  trialBalance: (params) => api.get('/accounting/reports/trial-balance' + qs(params)),
  profitLoss: (params) => api.get('/accounting/reports/profit-loss' + qs(params)),
  balanceSheet: (params) => api.get('/accounting/reports/balance-sheet' + qs(params)),
  cashFlow: (params) => api.get('/accounting/reports/cash-flow' + qs(params)),
  // Periods + backfill
  periods: () => api.get('/accounting/periods'),
  closePeriod: (body) => api.post('/accounting/periods/close', body),
  reopenPeriod: (id) => api.post(`/accounting/periods/${id}/reopen`, {}),
  removePeriod: (id) => api.del(`/accounting/periods/${id}`),
  backfill: () => api.post('/accounting/backfill', {}),
};

// Bank/payments reconciliation (Click/Payme ↔ debt, terminal ↔ card sales).
export const ReconciliationApi = {
  get: (params) => api.get('/accounting/reconciliation' + qs(params)),
  creditOnline: (id) => api.post(`/accounting/reconciliation/online/${id}/credit`, {}),
};

// Supplier purchase orders + receiving + costing (FIFO/WAC).
export const PurchaseOrderApi = {
  list: () => api.get('/purchase-orders'),
  get: (id) => api.get(`/purchase-orders/${id}`),
  create: (body) => api.post('/purchase-orders', body),
  update: (id, body) => api.put(`/purchase-orders/${id}`, body),
  order: (id) => api.post(`/purchase-orders/${id}/order`, {}),
  receive: (id, body) => api.post(`/purchase-orders/${id}/receive`, body),
  cancel: (id) => api.post(`/purchase-orders/${id}/cancel`, {}),
  remove: (id) => api.del(`/purchase-orders/${id}`),
};

export const CostingApi = {
  history: (productId) => api.get('/costing/history' + qs({ productId })),
  valuation: () => api.get('/costing/valuation'),
};

export const PaymentApi = {
  list: (params) => api.get('/payments' + qs(params)),
  create: (body) => api.post('/payments', body),
  update: (id, body) => api.put(`/payments/${id}`, body),
  remove: (id) => api.del(`/payments/${id}`),
  parties: (category) => api.get('/payments/parties' + qs({ category })),
};

export const SupplierApi = {
  list: () => api.get('/suppliers'),
  detail: (id) => api.get(`/suppliers/${id}`),
  create: (body) => api.post('/suppliers', body),
  update: (id, body) => api.put(`/suppliers/${id}`, body),
  remove: (id) => api.del(`/suppliers/${id}`),
};

export const TransferApi = {
  list: () => api.get('/transfers'),
  create: (body) => api.post('/transfers', body),
};

// Phase 4.3 hardware integration: thermal printer + cash drawer.
// All four endpoints respect the active shop's `printer_name`; if it's
// null the request falls back to the OS-default printer.
export const PrintApi = {
  // Names of every printer the OS knows about — fills the Shops-edit
  // modal's printer dropdown.
  listPrinters: () => api.get('/print/printers'),
  test: () => api.post('/print/test'),
  drawer: () => api.post('/print/drawer'),
  receipt: (body) => api.post('/print/receipt', body),
};
