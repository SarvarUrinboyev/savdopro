import { useMemo, useState } from 'react';
import { DeviceApi, ProductApi } from '../api/endpoints.js';
import { DeviceIntakeModal } from '../components/DeviceIntakeModal.jsx';
import { useToast } from '../components/Toast.jsx';
import { EmptyState, Loader, MetricCard, PageHeader } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { money } from '../lib/format.js';

const STATUS_BADGE = {
  IN_STOCK: { badge: 'badge-karta', label: 'Omborda' },
  SOLD: { badge: 'badge-naqd', label: 'Sotilgan' },
  BLOCKED: { badge: 'badge-qarzga', label: 'Bloklangan' },
  RETURNED: { badge: 'badge-muted', label: 'Qaytarilgan' },
};

const shortDate = (s) => (s ? s.replace('T', ' ').slice(0, 10) : '');

/**
 * Device (IMEI) register. Every IMEI-tracked unit is recorded at intake
 * (Omborda), flips to Sotilgan when sold (linked to the customer + date), and is
 * searchable here — so when a customer brings a device back you can verify "did
 * we sell this IMEI, when, and to whom?". The app never locks a phone; Samsung →
 * Knox Guard, iPhone → the shop's Apple ID + Find My.
 */
export function Devices() {
  const t = useT();
  const toast = useToast();
  const { data, loading, error, reload } = useApi(() => DeviceApi.list(), []);
  const productsApi = useApi(() => ProductApi.list(), []);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [onlyDebt, setOnlyDebt] = useState(false);
  const [busyId, setBusyId] = useState(null);
  const [intakeOpen, setIntakeOpen] = useState(false);

  const devices = data || [];
  const imeiProducts = useMemo(
    () => (productsApi.data || []).filter((p) => p.requiresImei),
    [productsApi.data],
  );

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return devices.filter((d) => {
      if (statusFilter && d.status !== statusFilter) return false;
      if (onlyDebt && d.paymentMethod !== 'QARZGA') return false;
      if (!q) return true;
      return [d.imei1, d.imei2, d.serialNumber, d.appleId, d.customerName, d.productName]
        .some((v) => v && String(v).toLowerCase().includes(q));
    });
  }, [devices, search, statusFilter, onlyDebt]);

  const summary = useMemo(() => ({
    inStock: devices.filter((d) => d.status === 'IN_STOCK').length,
    sold: devices.filter((d) => d.status === 'SOLD' || d.status === 'BLOCKED').length,
    debt: devices.filter((d) => d.paymentMethod === 'QARZGA' && d.status !== 'IN_STOCK').length,
    blocked: devices.filter((d) => d.status === 'BLOCKED').length,
  }), [devices]);

  const setStatus = async (device, status) => {
    const note = status === 'BLOCKED'
      ? (window.prompt(t('Izoh (ixtiyoriy) — masalan: qarz to‘lanmadi, Knox Guard bilan bloklandi'), device.note || '') ?? device.note)
      : device.note;
    setBusyId(device.id);
    try {
      await DeviceApi.setStatus(device.id, { status, note });
      toast.success(t('Saqlandi'));
      reload();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusyId(null);
    }
  };

  const exportCsv = () => {
    const header = ['IMEI1', 'IMEI2', 'Serial', 'Apple ID', 'Mahsulot', 'Mijoz', "To'lov",
      'Holat', 'Kirim', 'Sotilgan'];
    const rows = filtered.map((d) => [
      d.imei1, d.imei2, d.serialNumber, d.appleId, d.productName, d.customerName,
      d.paymentMethod, d.status, shortDate(d.intakeDate), shortDate(d.soldAt),
    ]);
    const esc = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const csv = [header, ...rows].map((r) => r.map(esc).join(',')).join('\n');
    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'imei-qurilmalar.csv';
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <>
      <PageHeader title={t('Qurilmalar (IMEI)')}
                  desc={t('Kirimdan sotuvgacha — har bir qurilma IMEI bo‘yicha kuzatuv')}>
        <button className="btn btn-ghost" onClick={exportCsv} disabled={filtered.length === 0}
                title={t('IMEI ro‘yxatini CSV qilib yuklab olish (Knox Guard uchun ham)')}>
          ⬇ {t('CSV (IMEI)')}
        </button>
        <button className="btn btn-primary" onClick={() => setIntakeOpen(true)}>
          + {t('IMEI kirim')}
        </button>
      </PageHeader>

      <div className="metrics section">
        <MetricCard tone="blue" icon="📦" label={t('Omborda (IMEI)')} value={summary.inStock}
                    currency={false} />
        <MetricCard tone="green" icon="✅" label={t('Sotilgan')} value={summary.sold}
                    currency={false} />
        <MetricCard tone="red" icon="📒" label={t('Qarzga')} value={summary.debt}
                    currency={false} />
        <MetricCard tone="muted" icon="🔒" label={t('Bloklangan')} value={summary.blocked}
                    currency={false} />
      </div>

      <div className="card card-pad section">
        <div className="flex gap-8" style={{ flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <div className="field" style={{ margin: 0, maxWidth: 320, flex: 1 }}>
            <label>{t('Qidiruv')}</label>
            <input className="input" placeholder={t('IMEI, seriya, mijoz yoki mahsulot...')}
                   value={search} onChange={(e) => setSearch(e.target.value)} />
          </div>
          <div className="field" style={{ margin: 0, maxWidth: 200 }}>
            <label>{t('Holat')}</label>
            <select className="select" value={statusFilter}
                    onChange={(e) => setStatusFilter(e.target.value)}>
              <option value="">{t('Hammasi')}</option>
              <option value="IN_STOCK">{t('Omborda')}</option>
              <option value="SOLD">{t('Sotilgan')}</option>
              <option value="BLOCKED">{t('Bloklangan')}</option>
              <option value="RETURNED">{t('Qaytarilgan')}</option>
            </select>
          </div>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer',
                          userSelect: 'none', paddingBottom: 8 }}>
            <input type="checkbox" checked={onlyDebt} onChange={(e) => setOnlyDebt(e.target.checked)}
                   style={{ width: 16, height: 16, accentColor: 'var(--green)' }} />
            {t('Faqat qarzga')}
          </label>
        </div>
      </div>

      <div className="card">
        <div className="card-head">
          <h2>{t('Qurilmalar ro‘yxati')}</h2>
          <span className="hint">{filtered.length} {t('ta')}</span>
        </div>
        <Loader loading={loading} error={error} onRetry={reload}>
          {filtered.length === 0 ? (
            <EmptyState icon="📱" text={t('Qurilma topilmadi')} />
          ) : (
            <div className="table-wrap">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>{t('Mahsulot')}</th>
                    <th>IMEI 1</th>
                    <th>IMEI 2</th>
                    <th>S/N</th>
                    <th>Apple ID</th>
                    <th>{t('Mijoz')}</th>
                    <th className="num">{t('Narx')}</th>
                    <th>{t('Holat')}</th>
                    <th>{t('Sana')}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((d) => {
                    const st = STATUS_BADGE[d.status] || STATUS_BADGE.IN_STOCK;
                    const sold = d.status === 'SOLD' || d.status === 'BLOCKED';
                    return (
                      <tr key={d.id}>
                        <td>{d.productName}</td>
                        <td style={{ fontFamily: 'monospace' }}>{d.imei1 || '—'}</td>
                        <td style={{ fontFamily: 'monospace' }}>{d.imei2 || '—'}</td>
                        <td style={{ fontFamily: 'monospace' }}>{d.serialNumber || '—'}</td>
                        <td>{d.appleId || '—'}</td>
                        <td>{d.customerName || (sold ? '—' : '')}</td>
                        <td className="num">{d.salePriceUzs != null ? money(d.salePriceUzs) : '—'}</td>
                        <td><span className={`badge ${st.badge}`}>{t(st.label)}</span></td>
                        <td className="faint" style={{ fontSize: 12 }}>
                          {sold
                            ? `${t('Sotildi')}: ${shortDate(d.soldAt)}`
                            : `${t('Kirim')}: ${shortDate(d.intakeDate)}`}
                        </td>
                        <td className="num">
                          {d.status === 'SOLD' && (
                            <button className="btn btn-ghost btn-sm" disabled={busyId === d.id}
                                    onClick={() => setStatus(d, 'BLOCKED')}>
                              🔒 {t('Bloklash')}
                            </button>
                          )}
                          {d.status === 'BLOCKED' && (
                            <button className="btn btn-ghost btn-sm" disabled={busyId === d.id}
                                    onClick={() => setStatus(d, 'SOLD')}>
                              🔓 {t('Ochish')}
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </Loader>
      </div>

      {intakeOpen && (
        <DeviceIntakeModal
          products={imeiProducts}
          onClose={() => setIntakeOpen(false)}
          onDone={() => { setIntakeOpen(false); reload(); productsApi.reload(); }}
        />
      )}
    </>
  );
}
