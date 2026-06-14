import { useMemo, useRef, useState } from 'react';
import { DeviceApi, ProductApi } from '../api/endpoints.js';
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
 * IMEI baza — the device register with a scanner-driven Kirim (scan in → IN_STOCK,
 * stock +1) and Chiqim (scan out → marked sold, stock −1), plus a searchable
 * register to verify "did we sell this IMEI, when, to whom?". The app never locks
 * a phone; Samsung → Knox Guard, iPhone → the shop's Apple ID + Find My.
 */
export function Devices() {
  const t = useT();
  const toast = useToast();
  const { data, loading, error, reload } = useApi(() => DeviceApi.list(), []);
  const productsApi = useApi(() => ProductApi.list(), []);

  const [tab, setTab] = useState('baza');

  // --- baza (register) filters ---
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [onlyDebt, setOnlyDebt] = useState(false);
  const [busyId, setBusyId] = useState(null);

  // --- kirim / chiqim scanners ---
  const [kirimProductId, setKirimProductId] = useState('');
  const [kirimCode, setKirimCode] = useState('');
  const [kirimLog, setKirimLog] = useState([]);
  const [chiqimCode, setChiqimCode] = useState('');
  const [chiqimLog, setChiqimLog] = useState([]);
  const [scanBusy, setScanBusy] = useState(false);
  const kirimRef = useRef(null);
  const chiqimRef = useRef(null);

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

  const refresh = () => { reload(); productsApi.reload(); };

  // ---- Kirim: scan one IMEI in ----
  const onKirimScan = async (raw) => {
    const code = (raw || '').trim();
    setKirimCode('');
    if (!code || scanBusy) return;
    if (!kirimProductId) {
      toast.error(t('Avval mahsulotni tanlang'));
      return;
    }
    setScanBusy(true);
    try {
      const res = await DeviceApi.intake({ productId: Number(kirimProductId), devices: [{ imei1: code }] });
      const d = res[0];
      setKirimLog((prev) => [{ id: Date.now(), ok: true, text: `${code} → ${d.productName}` }, ...prev]);
      refresh();
    } catch (err) {
      setKirimLog((prev) => [{ id: Date.now(), ok: false, text: `${code} — ${err.message}` }, ...prev]);
    } finally {
      setScanBusy(false);
      setTimeout(() => kirimRef.current?.focus(), 30);
    }
  };

  // ---- Chiqim: scan one IMEI out ----
  const onChiqimScan = async (raw) => {
    const code = (raw || '').trim();
    setChiqimCode('');
    if (!code || scanBusy) return;
    setScanBusy(true);
    try {
      const d = await DeviceApi.dispatch({ imei: code });
      setChiqimLog((prev) => [{ id: Date.now(), ok: true, text: `${code} ${t('chiqdi')} → ${d.productName}` }, ...prev]);
      refresh();
    } catch (err) {
      setChiqimLog((prev) => [{ id: Date.now(), ok: false, text: `${code} — ${err.message}` }, ...prev]);
    } finally {
      setScanBusy(false);
      setTimeout(() => chiqimRef.current?.focus(), 30);
    }
  };

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
    a.download = 'imei-baza.csv';
    a.click();
    URL.revokeObjectURL(url);
  };

  const tabBtn = (key, label) => (
    <button
      className={`btn ${tab === key ? 'btn-primary' : 'btn-ghost'}`}
      onClick={() => setTab(key)}
    >
      {label}
    </button>
  );

  return (
    <>
      <PageHeader title={t('IMEI baza')}
                  desc={t('Smartfon/Apple/elektronika qurilmalarini IMEI bo‘yicha kirim–chiqim qilish va kuzatish')}>
        {tab === 'baza' && (
          <button className="btn btn-ghost" onClick={exportCsv} disabled={filtered.length === 0}>
            ⬇ {t('CSV (IMEI)')}
          </button>
        )}
      </PageHeader>

      <div className="metrics section">
        <MetricCard tone="blue" icon="📦" label={t('Omborda (IMEI)')} value={summary.inStock} currency={false} />
        <MetricCard tone="green" icon="✅" label={t('Sotilgan')} value={summary.sold} currency={false} />
        <MetricCard tone="red" icon="📒" label={t('Qarzga')} value={summary.debt} currency={false} />
        <MetricCard tone="muted" icon="🔒" label={t('Bloklangan')} value={summary.blocked} currency={false} />
      </div>

      <div className="flex gap-8 section">
        {tabBtn('baza', `📋 ${t('Baza')}`)}
        {tabBtn('kirim', `⬇ ${t('IMEI kirim')}`)}
        {tabBtn('chiqim', `⬆ ${t('IMEI chiqim')}`)}
      </div>

      {tab === 'kirim' && (
        <div className="card card-pad section">
          <div className="field" style={{ maxWidth: 420 }}>
            <label>{t('Mahsulot (IMEI kuzatiladigan)')}</label>
            <select className="select" value={kirimProductId}
                    onChange={(e) => setKirimProductId(e.target.value)}>
              <option value="">{t('Tanlang...')}</option>
              {imeiProducts.map((p) => (
                <option key={p.id} value={p.id}>{p.name} ({t('qoldiq')}: {p.quantity})</option>
              ))}
            </select>
            {imeiProducts.length === 0 && (
              <div className="field-hint">
                {t('IMEI kuzatiladigan mahsulot yo‘q. Ombor → mahsulotda “Sotuvda IMEI talab qilinsin” ni yoqing.')}
              </div>
            )}
          </div>
          <div className="field" style={{ maxWidth: 420 }}>
            <label>{t('IMEI ni skanerlang (yoki yozing) + Enter')}</label>
            <input ref={kirimRef} className="input" autoFocus value={kirimCode}
                   disabled={scanBusy || !kirimProductId}
                   onChange={(e) => setKirimCode(e.target.value)}
                   onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); onKirimScan(e.target.value); } }}
                   placeholder={t('Telefon qutisidagi IMEI shtrix kodini skanerlang')}
                   style={{ fontSize: 18, fontFamily: 'monospace' }} />
            <div className="field-hint">
              {t('Har bir skanerda IMEI bazaga “Omborda” bo‘lib tushadi va qoldiq +1 bo‘ladi.')}
            </div>
          </div>
          <ScanLog title={`${t('Kirim qilindi')}`} log={kirimLog} t={t} />
        </div>
      )}

      {tab === 'chiqim' && (
        <div className="card card-pad section">
          <div className="field" style={{ maxWidth: 420 }}>
            <label>{t('IMEI ni skanerlang (yoki yozing) + Enter')}</label>
            <input ref={chiqimRef} className="input" autoFocus value={chiqimCode}
                   disabled={scanBusy}
                   onChange={(e) => setChiqimCode(e.target.value)}
                   onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); onChiqimScan(e.target.value); } }}
                   placeholder={t('Chiqim uchun IMEI ni skanerlang')}
                   style={{ fontSize: 18, fontFamily: 'monospace' }} />
            <div className="field-hint">
              {t('Skanerlangan IMEI bazadan “Sotilgan” bo‘ladi va qoldiq −1 bo‘ladi. Omborda bo‘lmasa, ogohlantiradi.')}
            </div>
          </div>
          <ScanLog title={`${t('Chiqim qilindi')}`} log={chiqimLog} t={t} />
        </div>
      )}

      {tab === 'baza' && (
        <>
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
        </>
      )}
    </>
  );
}

/** Live feed of the last scanned units (green = ok, red = error). */
function ScanLog({ title, log, t }) {
  return (
    <div className="card" style={{ marginTop: 12 }}>
      <div className="card-head">
        <h2>{title}</h2>
        <span className="hint">{log.length} {t('ta')}</span>
      </div>
      <div className="card-pad">
        {log.length === 0 ? (
          <div className="faint" style={{ fontSize: 13 }}>{t('Hali skanerlanmadi.')}</div>
        ) : (
          <div className="list-stack">
            {log.slice(0, 30).map((e) => (
              <div key={e.id} style={{ fontSize: 13 }}>
                <b className={e.ok ? 'amount-pos' : 'amount-neg'}>{e.ok ? '✓ ' : '✗ '}</b>
                {e.text}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
