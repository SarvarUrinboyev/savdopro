import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CategoryApi, ProductApi, ReportApi } from '../api/endpoints.js';
import { downloadAuthed } from '../lib/download.js';
import { CategoryManager } from '../components/CategoryManager.jsx';
import { ExportButton } from '../components/ExportButton.jsx';
import { ImportModal } from '../components/ImportModal.jsx';
import { ScanModal } from '../components/ScanModal.jsx';
import { EmptyState, Loader, MetricCard } from '../components/ui.jsx';
import { useAuth } from '../context/Auth.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { money, usd } from '../lib/format.js';

const STATUS_LABEL = { IN_STOCK: 'Mavjud', LOW: 'Kam qoldi', OUT: 'Tugagan' };
const STATUS_BADGE = { IN_STOCK: 'badge-naqd', LOW: 'badge-karta', OUT: 'badge-qarzga' };

// Expiry helper: red if already expired, amber within 30 days, otherwise none.
function expiryInfo(dateStr) {
  if (!dateStr) return null;
  const d = new Date(String(dateStr).slice(0, 10) + 'T00:00:00');
  if (Number.isNaN(d.getTime())) return null;
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const days = Math.round((d - today) / 86400000);
  if (days < 0) return { label: "Muddati o'tgan", color: '#ef4444' };
  if (days <= 30) return { label: days + ' kun qoldi', color: '#f59e0b' };
  return null;
}

function ExpiryBadge({ date }) {
  const info = expiryInfo(date);
  if (!info) return null;
  return (
    <span style={{
      marginLeft: 8, fontSize: 11, fontWeight: 700, padding: '2px 7px',
      borderRadius: 6, color: '#fff', background: info.color, whiteSpace: 'nowrap',
    }}>
      ⏰ {info.label}
    </span>
  );
}

export function Warehouse() {
  const t = useT();
  const navigate = useNavigate();
  const { user } = useAuth();
  // Goods transfer is owner/admin-only (same gate as the old sidebar link + the
  // /transfers route). We surface it here, inside the warehouse.
  const canTransfer = user?.role === 'ACCOUNT_OWNER' || user?.role === 'SUPER_ADMIN';
  const { data, loading, error, reload } = useApi(
    () => Promise.all([ProductApi.list(), CategoryApi.list()]),
    [],
  );
  const [search, setSearch] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [status, setStatus] = useState('');
  const [modal, setModal] = useState(null);

  const products = data ? data[0] : [];
  const categories = data ? data[1] : [];

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return products.filter((p) => {
      if (q
          && !p.name.toLowerCase().includes(q)
          && !(p.barcode && p.barcode.toLowerCase().includes(q))
          && !(p.imei1 && p.imei1.toLowerCase().includes(q))
          && !(p.imei2 && p.imei2.toLowerCase().includes(q))) {
        return false;
      }
      if (categoryId && String(p.categoryId) !== categoryId) {
        return false;
      }
      return !(status && p.stockStatus !== status);
    });
  }, [products, search, categoryId, status]);

  const summary = useMemo(() => {
    let value = 0;
    let profit = 0;
    for (const p of products) {
      value += Number(p.stockValue);
      profit += Number(p.margin) * p.quantity;
    }
    return { count: products.length, value, profit };
  }, [products]);

  return (
    <>
      <div className="page-head">
        <div>
          <div style={{
            fontSize: 11, fontWeight: 700, letterSpacing: '.09em',
            color: 'var(--text-faint)',
          }}>
            {t('OMBOR')}
          </div>
          <h1>{t('Mahsulotlar')}</h1>
          <div className="desc">
            {t("Mahsulot qo'shing, narx va ombor miqdorini boshqaring, CSV/XLSX orqali yuklang.")}
          </div>
        </div>
        <div className="actions">
          <button className="btn btn-ghost" onClick={() => setModal('categories')}>
            🗂 {t('Toifalar')}
          </button>
          <button className="btn btn-ghost" onClick={() => setModal('import')}>
            ⬇ {t('Import')}
          </button>
          <button
            className="btn btn-ghost"
            title={t('Joriy ombor qoldiqlarini PDF qilib yuklab olish')}
            onClick={async () => {
              try {
                await downloadAuthed(
                  ReportApi.inventoryPdfUrl(null),
                  `ombor-${new Date().toISOString().slice(0, 10)}.pdf`,
                );
              } catch (err) { window.alert(err.message); }
            }}
          >
            📄 {t('PDF eksport')}
          </button>
          <ExportButton
            filename={`ombor-${new Date().toISOString().slice(0, 10)}`}
            getRows={() => filtered.map((p) => ({
              [t('Nomi')]: p.name,
              [t('Shtrix-kod')]: p.barcode || '',
              [t('Toifa')]: p.categoryName || '',
              [t('Qoldiq')]: p.quantity,
              [t('Kelish narxi (USD)')]: p.purchasePrice ?? '',
              [t('Sotilish narxi (USD)')]: p.salePrice ?? '',
              [t('Past stok pol')]: p.lowStockThreshold ?? '',
              [t('Holat')]: t(STATUS_LABEL[p.stockStatus] || p.stockStatus),
            }))}
          />
          <button className="btn btn-accent" onClick={() => setModal('scan')}>
            📷 {t('Skaner')}
          </button>
          {canTransfer && (
            <button className="btn btn-ghost" onClick={() => navigate('/transfers')}>
              🔁 {t('Tovar transferi')}
            </button>
          )}
          <button className="btn btn-ghost" onClick={() => navigate('/stocktake')}>
            📋 {t('Inventarizatsiya')}
          </button>
          <button className="btn btn-ghost" onClick={() => navigate('/purchase-orders')}>
            📦 {t('Yetkazib beruvchi buyurtmalari')}
          </button>
          <button className="btn btn-primary" onClick={() => navigate('/warehouse/new')}>
            + {t('Yangi mahsulot')}
          </button>
        </div>
      </div>

      <div
        className="section"
        style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16 }}
      >
        <MetricCard tone="blue" icon="📦" label={t('Mahsulot turlari')} value={summary.count}
                    currency={false} />
        <MetricCard tone="amber" icon="🏬" label={t('Ombor qiymati (kelish)')} value={summary.value} />
        <MetricCard tone="green" icon="📈" label={t('Potensial foyda')} value={summary.profit} />
      </div>

      <div className="card card-pad section">
        <div
          className="form-row"
          style={{ gridTemplateColumns: '2fr 1fr 1fr auto', alignItems: 'flex-end', gap: 12 }}
        >
          <div className="field" style={{ margin: 0 }}>
            <label>{t('Qidiruv')}</label>
            <input
              className="input"
              placeholder={t('Nomi yoki IMEI...')}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <div className="field" style={{ margin: 0 }}>
            <label>{t('Toifa')}</label>
            <select className="select" value={categoryId}
                    onChange={(e) => setCategoryId(e.target.value)}>
              <option value="">{t('Barcha toifalar')}</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </div>
          <div className="field" style={{ margin: 0 }}>
            <label>{t('Holat')}</label>
            <select className="select" value={status}
                    onChange={(e) => setStatus(e.target.value)}>
              <option value="">{t('Barcha holatlar')}</option>
              <option value="IN_STOCK">{t('Mavjud')}</option>
              <option value="LOW">{t('Kam qoldi')}</option>
              <option value="OUT">{t('Tugagan')}</option>
            </select>
          </div>
          <button
            className="btn btn-ghost"
            onClick={() => { setSearch(''); setCategoryId(''); setStatus(''); }}
          >
            {t('Tozalash')}
          </button>
        </div>
      </div>

      <div className="card">
        <div className="card-head">
          <h2>{t("Mahsulotlar ro'yxati")}</h2>
          <span className="hint">{filtered.length} {t('ta')}</span>
        </div>
        <Loader loading={loading} error={error} onRetry={reload}>
          {filtered.length === 0 ? (
            <EmptyState icon="🏬" text={t('Mahsulot topilmadi')} />
          ) : (
            <div className="table-wrap">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>{t('Nomi')}</th>
                    <th>{t('IMEI')}</th>
                    <th className="num">{t('Narx')}</th>
                    <th className="num">{t('Miqdor')}</th>
                    <th>{t('Holat')}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((p) => (
                    <tr
                      key={p.id}
                      style={{ cursor: 'pointer' }}
                      onClick={() => navigate(`/warehouse/${p.id}`)}
                    >
                      <td className="name-cell">{p.name}<ExpiryBadge date={p.expiryDate} /></td>
                      <td className="faint mono">{p.imei1 || '—'}</td>
                      <td className="num">{usd(p.salePrice)}</td>
                      <td
                        className="num"
                        style={{
                          color: p.stockStatus === 'OUT' ? 'var(--red)'
                            : p.stockStatus === 'LOW' ? 'var(--amber)' : undefined,
                        }}
                      >
                        {p.quantity}
                      </td>
                      <td>
                        <span className={`badge ${STATUS_BADGE[p.stockStatus]}`}>
                          {t(STATUS_LABEL[p.stockStatus])}
                        </span>
                      </td>
                      <td className="right">
                        <span className="btn btn-ghost btn-sm">{t('Tahrirlash')} →</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Loader>
      </div>

      {modal === 'categories' && (
        <CategoryManager onClose={() => { setModal(null); reload(); }} />
      )}
      {modal === 'import' && (
        <ImportModal onClose={() => setModal(null)} onDone={reload} />
      )}
      {modal === 'scan' && (
        <ScanModal
          categories={categories}
          onChanged={reload}
          onClose={() => setModal(null)}
        />
      )}
    </>
  );
}
