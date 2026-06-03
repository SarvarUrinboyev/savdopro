import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CustomerApi, ProductApi } from '../api/endpoints.js';
import { useT } from '../context/Settings.jsx';
import { useKeyboard } from '../hooks/useKeyboard.js';

/**
 * Command-palette modal — opens with Ctrl+K (or "/").
 *
 * Three-section results:
 *   • Sahifalar     — hardcoded NAV list (router paths)
 *   • Mahsulotlar   — product search via /api/products?search=…
 *   • Mijozlar      — customer search via /api/customers?search=…
 *
 * Network calls are debounced (180 ms) so each keystroke doesn't hit
 * the backend, and the request is aborted on the next change so a slow
 * response can't overwrite a newer one.
 *
 * Keyboard:
 *   ↑/↓ — navigate, Enter — open, Esc — close.
 */

const PAGES = [
  { label: 'Boshqaruv', path: '/dashboard' },
  { label: 'Moliya', path: '/management' },
  { label: "Do'kon xarajatlari", path: '/home-expenses' },
  { label: "To'lov", path: '/payments' },
  { label: 'Buyurtmalar', path: '/orders' },
  { label: 'Ombor', path: '/warehouse' },
  { label: 'Mijozlar', path: '/customers' },
  { label: 'Yetkazib beruvchilar', path: '/suppliers' },
  { label: 'Qarz', path: '/debt' },
  { label: 'Kalkulyator', path: '/calculator' },
  { label: 'Hisobotlar', path: '/reports' },
  { label: "Do'konlar", path: '/shops' },
  { label: 'Tovar transferi', path: '/transfers' },
  { label: 'Audit log', path: '/admin/audit' },
];

export function QuickSearch() {
  const t = useT();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [products, setProducts] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [selectedIdx, setSelectedIdx] = useState(0);
  const inputRef = useRef(null);
  const abortRef = useRef(null);

  // Global shortcuts: Ctrl+K toggles, "/" opens, Escape closes.
  useKeyboard(
    useMemo(() => ({
      'ctrl+k': () => setOpen((v) => !v),
      '/': () => setOpen(true),
      Escape: () => setOpen(false),
    }), []),
  );

  // Focus the input when the modal opens.
  useEffect(() => {
    if (open) {
      setTimeout(() => inputRef.current?.focus(), 30);
      setSelectedIdx(0);
    } else {
      setQuery('');
      setProducts([]);
      setCustomers([]);
    }
  }, [open]);

  // Debounced backend search.
  useEffect(() => {
    if (!open) return undefined;
    const q = query.trim();
    if (q.length < 2) {
      setProducts([]); setCustomers([]);
      return undefined;
    }
    if (abortRef.current) abortRef.current = null;
    const timer = setTimeout(async () => {
      const my = {};
      abortRef.current = my;
      try {
        const [ps, cs] = await Promise.all([
          ProductApi.list({ search: q }).catch(() => []),
          CustomerApi.list({ search: q }).catch(() => []),
        ]);
        if (abortRef.current !== my) return;
        setProducts((ps || []).slice(0, 5));
        setCustomers((cs || []).slice(0, 5));
      } catch { /* network error — keep last results */ }
    }, 180);
    return () => clearTimeout(timer);
  }, [query, open]);

  // Flat list of selectable items so ↑/↓ + Enter work without per-section logic.
  const items = useMemo(() => {
    const filter = (label) => label.toLowerCase().includes(query.trim().toLowerCase());
    const pageHits = query.trim()
      ? PAGES.filter((p) => filter(p.label))
      : PAGES.slice(0, 6);
    return [
      ...pageHits.map((p) => ({ kind: 'page', label: p.label, sub: p.path, action: () => navigate(p.path) })),
      ...products.map((p) => ({ kind: 'product', label: p.name, sub: `${t('Qoldiq')}: ${p.quantity}`, action: () => navigate(`/warehouse/${p.id}`) })),
      ...customers.map((c) => ({ kind: 'customer', label: c.name || c.fullName || '(?)', sub: c.phone || '', action: () => navigate(`/customers/${c.id}`) })),
    ];
  }, [products, customers, query, navigate, t]);

  const onPick = useCallback((idx) => {
    const it = items[idx];
    if (!it) return;
    it.action();
    setOpen(false);
  }, [items]);

  if (!open) return null;
  return (
    <div className="qs-backdrop" onMouseDown={() => setOpen(false)}>
      <div className="qs-modal" onMouseDown={(e) => e.stopPropagation()}>
        <input
          ref={inputRef}
          className="qs-input"
          type="search"
          placeholder={t('Sahifa, mahsulot, mijoz...')}
          value={query}
          onChange={(e) => { setQuery(e.target.value); setSelectedIdx(0); }}
          onKeyDown={(e) => {
            if (e.key === 'ArrowDown') {
              e.preventDefault();
              setSelectedIdx((i) => Math.min(items.length - 1, i + 1));
            } else if (e.key === 'ArrowUp') {
              e.preventDefault();
              setSelectedIdx((i) => Math.max(0, i - 1));
            } else if (e.key === 'Enter') {
              e.preventDefault();
              onPick(selectedIdx);
            }
          }}
        />
        <div className="qs-results">
          {items.length === 0 ? (
            <div className="qs-empty">{t('Hech narsa topilmadi')}</div>
          ) : (
            items.map((it, idx) => (
              <button
                key={idx}
                className={`qs-item ${selectedIdx === idx ? 'on' : ''}`}
                onMouseEnter={() => setSelectedIdx(idx)}
                onClick={() => onPick(idx)}
              >
                <span className="qs-ico">{iconFor(it.kind)}</span>
                <span className="qs-label">{it.label}</span>
                <span className="qs-sub faint">{it.sub}</span>
              </button>
            ))
          )}
        </div>
        <div className="qs-foot faint">
          <span>↑/↓ {t("o'tish")}</span>
          <span>↵ {t('ochish')}</span>
          <span>Esc {t('yopish')}</span>
        </div>
      </div>
    </div>
  );
}

function iconFor(kind) {
  if (kind === 'page') return '📄';
  if (kind === 'product') return '📦';
  if (kind === 'customer') return '👤';
  return '•';
}
