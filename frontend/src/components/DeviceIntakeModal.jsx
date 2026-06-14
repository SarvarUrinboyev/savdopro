import { useState } from 'react';
import { DeviceApi } from '../api/endpoints.js';
import { useT } from '../context/Settings.jsx';
import { Modal } from './Modal.jsx';
import { useToast } from './Toast.jsx';

/**
 * Register incoming units' IMEIs at intake (kirim). Pick an IMEI-tracked product,
 * enter one row per physical unit, save → each becomes an IN_STOCK device and the
 * product's stock goes up by the count. For IMEI products this IS the stock-in —
 * don't also do a plain quantity kirim for the same units.
 */
export function DeviceIntakeModal({ products, onClose, onDone }) {
  const t = useT();
  const toast = useToast();
  const [productId, setProductId] = useState('');
  const [rows, setRows] = useState(
    () => Array.from({ length: 3 }, () => ({ imei1: '', imei2: '', serial: '', appleId: '' })),
  );
  const [busy, setBusy] = useState(false);

  const setField = (i, f, v) => setRows((prev) => prev.map((r, idx) => (idx === i ? { ...r, [f]: v } : r)));
  const addRow = () => setRows((prev) => [...prev, { imei1: '', imei2: '', serial: '', appleId: '' }]);

  const submit = async () => {
    if (busy) return;
    if (!productId) {
      toast.error(t('Mahsulotni tanlang'));
      return;
    }
    const devices = rows
      .map((r) => ({
        imei1: r.imei1.trim(), imei2: r.imei2.trim(), serial: r.serial.trim(), appleId: r.appleId.trim(),
      }))
      .filter((d) => d.imei1 || d.imei2 || d.serial || d.appleId);
    if (devices.length === 0) {
      toast.error(t('Kamida bitta IMEI kiriting'));
      return;
    }
    setBusy(true);
    try {
      const res = await DeviceApi.intake({ productId: Number(productId), devices });
      toast.success(`${res.length} ${t('qurilma omborga qo‘shildi')}`);
      onDone();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      title={t('IMEI kirim — qurilma qabul qilish')}
      wide
      onClose={onClose}
      footer={(
        <div className="flex gap-8" style={{ justifyContent: 'flex-end', width: '100%' }}>
          <button className="btn btn-ghost" onClick={onClose} disabled={busy}>{t('Bekor')}</button>
          <button className="btn btn-primary" onClick={submit} disabled={busy}>
            {busy ? t('Saqlanmoqda...') : t('Omborga qabul qilish')}
          </button>
        </div>
      )}
    >
      <div className="field">
        <label>{t('Mahsulot (IMEI kuzatiladigan)')}</label>
        <select className="select" value={productId} onChange={(e) => setProductId(e.target.value)}>
          <option value="">{t('Tanlang...')}</option>
          {products.map((p) => (
            <option key={p.id} value={p.id}>{p.name} ({t('qoldiq')}: {p.quantity})</option>
          ))}
        </select>
        {products.length === 0 && (
          <div className="field-hint">
            {t('IMEI kuzatiladigan mahsulot yo‘q. Avval Ombor → mahsulotda “Sotuvda IMEI talab qilinsin” ni yoqing.')}
          </div>
        )}
      </div>

      <div className="field-hint" style={{ margin: '4px 0 8px' }}>
        {t('Har bir dona uchun bitta qator. IMEI 1 asosiy; qolganlari ixtiyoriy. Bo‘sh qatorlar e’tiborga olinmaydi.')}
      </div>

      <div className="list-stack">
        {rows.map((r, i) => (
          <div key={i} className="card card-pad" style={{ padding: 8 }}>
            <div className="field-hint" style={{ marginBottom: 4 }}>{t('Dona')} #{i + 1}</div>
            <div className="form-row">
              <input className="input" value={r.imei1} inputMode="numeric"
                     onChange={(e) => setField(i, 'imei1', e.target.value)}
                     placeholder={t('IMEI 1 (15 raqam)')} style={{ fontFamily: 'monospace' }} />
              <input className="input" value={r.imei2} inputMode="numeric"
                     onChange={(e) => setField(i, 'imei2', e.target.value)}
                     placeholder={t('IMEI 2 (ixtiyoriy)')} style={{ fontFamily: 'monospace' }} />
            </div>
            <div className="form-row" style={{ marginTop: 6 }}>
              <input className="input" value={r.serial}
                     onChange={(e) => setField(i, 'serial', e.target.value)}
                     placeholder={t('Seriya (S/N, ixtiyoriy)')} style={{ fontFamily: 'monospace' }} />
              <input className="input" value={r.appleId} inputMode="email"
                     onChange={(e) => setField(i, 'appleId', e.target.value)}
                     placeholder={t('Apple ID (iPhone, ixtiyoriy)')} />
            </div>
          </div>
        ))}
      </div>

      <button className="btn btn-ghost" style={{ marginTop: 8 }} onClick={addRow} disabled={busy}>
        + {t('Yana qator qo‘shish')}
      </button>
    </Modal>
  );
}
