import { useMemo, useState } from 'react';
import { ProductApi } from '../api/endpoints.js';
import { useToast } from '../components/Toast.jsx';
import { Loader, PageHeader } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';

/**
 * Inventarizatsiya — bulk stock count. The operator enters the physically
 * counted quantity for each product; on save the backend sets that quantity
 * and logs the difference as a CORRECTION movement.
 */
export function Stocktake() {
  const t = useT();
  const toast = useToast();
  const { data, loading, error, reload } = useApi(() => ProductApi.list(), []);
  const [counts, setCounts] = useState({}); // productId -> entered string
  const [search, setSearch] = useState('');
  const [busy, setBusy] = useState(false);

  const products = Array.isArray(data) ? data : (data?.content ?? []);
  const list = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return products;
    return products.filter((p) =>
      p.name.toLowerCase().includes(q) || String(p.barcode || '').includes(q));
  }, [products, search]);

  const payloadCounts = Object.entries(counts)
    .filter(([, v]) => v !== '' && !Number.isNaN(Number(v)))
    .map(([id, v]) => ({ productId: Number(id), actual: Number(v) }));

  const save = async () => {
    if (!payloadCounts.length) { toast.error(t('Hech narsa kiritilmadi')); return; }
    setBusy(true);
    try {
      const changed = await ProductApi.stocktake({ counts: payloadCounts });
      toast.success(`${changed.length} ${t('mahsulot qoldig\'i tuzatildi')}`);
      setCounts({});
      reload();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Loader loading={loading} error={error} onRetry={reload}>
      <PageHeader
        title={t('Inventarizatsiya')}
        desc={t("Haqiqiy qoldiqni sanab kiriting — farq avtomatik tuzatiladi")}
      />
      <div className="card section">
        <div className="card-pad">
          <input
            className="input"
            placeholder={t('Mahsulot qidirish...')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{ marginBottom: 14 }}
          />
          <div style={{ overflowX: 'auto' }}>
            <table className="table">
              <thead>
                <tr>
                  <th>{t('Nomi')}</th>
                  <th className="num">{t('Tizimda')}</th>
                  <th className="num">{t('Sanaldi')}</th>
                  <th className="num">{t('Farq')}</th>
                </tr>
              </thead>
              <tbody>
                {list.map((p) => {
                  const v = counts[p.id] ?? '';
                  const diff = v === '' ? null : Number(v) - p.quantity;
                  const color = diff === null ? 'inherit'
                    : diff === 0 ? 'var(--muted, #94a3b8)'
                      : diff > 0 ? '#16a34a' : '#ef4444';
                  return (
                    <tr key={p.id}>
                      <td>{p.name}</td>
                      <td className="num">{p.quantity}</td>
                      <td className="num">
                        <input
                          className="input"
                          type="number"
                          min="0"
                          inputMode="numeric"
                          style={{ width: 100, textAlign: 'right', padding: '6px 8px' }}
                          value={v}
                          onChange={(e) => setCounts((c) => ({ ...c, [p.id]: e.target.value }))}
                        />
                      </td>
                      <td className="num" style={{ fontWeight: 700, color }}>
                        {diff === null ? '—' : diff > 0 ? `+${diff}` : diff}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      </div>
      <div style={{ textAlign: 'right', padding: '4px 0 24px' }}>
        <span className="faint" style={{ marginRight: 14 }}>
          {payloadCounts.length} {t('ta kiritildi')}
        </span>
        <button className="btn btn-primary" disabled={busy || !payloadCounts.length} onClick={save}>
          {busy ? t('Saqlanmoqda...') : t('Saqlash va tuzatish')}
        </button>
      </div>
    </Loader>
  );
}
