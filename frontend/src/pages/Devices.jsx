import { useMemo, useState } from 'react';
import { DeviceApi } from '../api/endpoints.js';
import { useToast } from '../components/Toast.jsx';
import { EmptyState, Loader, MetricCard, PageHeader } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { money } from '../lib/format.js';

const STATUS_BADGE = {
  ACTIVE: { badge: 'badge-naqd', label: 'Faol' },
  BLOCKED: { badge: 'badge-qarzga', label: 'Bloklangan' },
  RETURNED: { badge: 'badge-muted', label: 'Qaytarilgan' },
};

/**
 * Sold-device (IMEI) register. Lists every phone/electronics unit handed to a
 * customer with its IMEI/serial, links to the sale + customer, and lets the
 * owner mark a device BLOCKED (e.g. after a Knox Guard lock) or export the IMEIs
 * as CSV (ready to upload into a Knox Guard device list). The app never locks a
 * phone itself — this is a record + the data feed for Knox Guard.
 */
export function Devices() {
  const t = useT();
  const toast = useToast();
  const { data, loading, error, reload } = useApi(() => DeviceApi.list(), []);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [onlyDebt, setOnlyDebt] = useState(false);
  const [busyId, setBusyId] = useState(null);

  const devices = data || [];

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return devices.filter((d) => {
      if (statusFilter && d.status !== statusFilter) return false;
      if (onlyDebt && d.paymentMethod !== 'QARZGA') return false;
      if (!q) return true;
      return [d.imei1, d.imei2, d.serialNumber, d.customerName, d.productName]
        .some((v) => v && String(v).toLowerCase().includes(q));
    });
  }, [devices, search, statusFilter, onlyDebt]);

  const summary = useMemo(() => ({
    count: devices.length,
    debt: devices.filter((d) => d.paymentMethod === 'QARZGA').length,
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
    const header = ['IMEI1', 'IMEI2', 'Serial', 'Mahsulot', 'Mijoz', "To'lov", 'Holat', 'Sana'];
    const rows = filtered.map((d) => [
      d.imei1, d.imei2, d.serialNumber, d.productName, d.customerName,
      d.paymentMethod, d.status, d.soldAt ? d.soldAt.replace('T', ' ').slice(0, 16) : '',
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
                  desc={t('Sotilgan smartfon/elektronika qurilmalari — IMEI bo‘yicha kuzatuv')}>
        <button className="btn btn-ghost" onClick={exportCsv} disabled={filtered.length === 0}
                title={t('IMEI ro‘yxatini CSV qilib yuklab olish (Knox Guard uchun ham)')}>
          ⬇ {t('CSV (IMEI)')}
        </button>
      </PageHeader>

      <div className="metrics section">
        <MetricCard tone="blue" icon="📱" label={t('Jami qurilmalar')} value={summary.count}
                    currency={false} />
        <MetricCard tone="red" icon="📒" label={t('Qarzga berilgan')} value={summary.debt}
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
              <option value="ACTIVE">{t('Faol')}</option>
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
                    <th>{t('Mijoz')}</th>
                    <th className="num">{t('Narx')}</th>
                    <th>{t("To'lov")}</th>
                    <th>{t('Holat')}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((d) => {
                    const st = STATUS_BADGE[d.status] || STATUS_BADGE.ACTIVE;
                    return (
                      <tr key={d.id}>
                        <td>{d.productName}</td>
                        <td style={{ fontFamily: 'monospace' }}>{d.imei1 || '—'}</td>
                        <td style={{ fontFamily: 'monospace' }}>{d.imei2 || '—'}</td>
                        <td style={{ fontFamily: 'monospace' }}>{d.serialNumber || '—'}</td>
                        <td>{d.customerName || '—'}</td>
                        <td className="num">{d.salePriceUzs != null ? money(d.salePriceUzs) : '—'}</td>
                        <td>
                          {d.paymentMethod === 'QARZGA'
                            ? <span className="badge badge-qarzga">{t('Qarzga')}</span>
                            : (d.paymentMethod || '—')}
                        </td>
                        <td><span className={`badge ${st.badge}`}>{t(st.label)}</span></td>
                        <td className="num">
                          {d.status !== 'BLOCKED' ? (
                            <button className="btn btn-ghost btn-sm" disabled={busyId === d.id}
                                    onClick={() => setStatus(d, 'BLOCKED')}>
                              🔒 {t('Bloklash')}
                            </button>
                          ) : (
                            <button className="btn btn-ghost btn-sm" disabled={busyId === d.id}
                                    onClick={() => setStatus(d, 'ACTIVE')}>
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
    </>
  );
}
