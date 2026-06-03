// Canonical list of sidebar modules the super-admin can toggle per account.
//
// `key` is the stable identifier persisted in accounts.enabled_modules (CSV).
// `label` is what the AccountDetail page (and the sidebar) display.
// `path` is the React-router URL the sidebar nav-link points at — used
// only by the sidebar filter; not stored anywhere.
//
// Adding a new module:
//   1. Append a row here.
//   2. Mark the matching <NavLink> in Sidebar.jsx with the same key.
// No schema migration is required.

export const ALL_MODULES = [
  { key: 'dashboard',     path: '/dashboard',     label: 'Boshqaruv' },
  { key: 'pos',           path: '/pos',           label: 'Kassa (POS)' },
  { key: 'pos-history',   path: '/pos/history',   label: 'Sotuvlar tarixi' },
  { key: 'promos',        path: '/promos',        label: 'Aksiyalar' },
  { key: 'management',    path: '/management',    label: 'Menejment' },
  { key: 'home-expenses', path: '/home-expenses', label: "Do'kon xarajatlari" },
  { key: 'payments',      path: '/payments',      label: "To'lov" },
  { key: 'orders',        path: '/orders',        label: 'Buyurtmalar' },
  { key: 'warehouse',     path: '/warehouse',     label: 'Ombor' },
  { key: 'customers',     path: '/customers',     label: 'Mijozlar' },
  { key: 'suppliers',     path: '/suppliers',     label: 'Yetkazib beruvchilar' },
  { key: 'debt',          path: '/debt',          label: 'Qarz' },
  { key: 'calculator',    path: '/calculator',    label: 'Kalkulyator' },
  { key: 'reports',       path: '/reports',       label: 'Hisobotlar' },
  { key: 'shops',         path: '/shops',         label: "Do'konlar" },
  { key: 'transfers',     path: '/transfers',     label: "Tovar transferi" },
];

/**
 * Parse a CSV allow-list into the set of enabled keys.
 *
 * @param {string|null|undefined} csv  raw value from accounts.enabled_modules
 * @param {string[]} allKeys           the canonical key list, used as the
 *                                     fallback when csv is null/blank
 *                                     (null = "all modules visible")
 * @returns {string[]} de-duped, normalized list of keys
 */
export function modulesFromCsv(csv, allKeys) {
  if (csv === null || csv === undefined || String(csv).trim() === '') {
    return [...allKeys];
  }
  const seen = new Set();
  String(csv).split(',').forEach((raw) => {
    const k = (raw || '').trim().toLowerCase();
    if (k) seen.add(k);
  });
  return [...seen];
}

/**
 * True when the given module key should be visible to a user whose
 * /me payload returned `enabledModules = csv`.
 *
 * Null / empty CSV always returns true (legacy default = all visible).
 */
export function isModuleEnabled(csv, key) {
  if (csv === null || csv === undefined || String(csv).trim() === '') {
    return true;
  }
  const norm = String(csv).toLowerCase();
  return norm.split(',').map((s) => s.trim()).includes(key);
}
