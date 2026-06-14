import { useState } from 'react';
import { useT } from '../context/Settings.jsx';
import { Modal } from './Modal.jsx';

/**
 * Collects each unit's IMEI / serial for IMEI-tracked cart lines, shown right
 * before payment. One block per product, one row per unit (line quantity).
 *
 * Nothing is mandatory — a cashier can leave a unit blank (IMEI not to hand) and
 * still continue; only filled rows are recorded. {@code onConfirm} receives a map
 * of {productId: [{imei1, imei2, serial}, ...]} (already trimmed, blanks dropped).
 */
export function ImeiCaptureModal({ lines, onConfirm, onClose }) {
  const t = useT();
  const [inputs, setInputs] = useState(() => {
    const init = {};
    for (const line of lines) {
      init[line.id] = Array.from({ length: Math.max(1, line.qty) },
        () => ({ imei1: '', imei2: '', serial: '', appleId: '' }));
    }
    return init;
  });

  const setField = (id, idx, field, value) => setInputs((prev) => ({
    ...prev,
    [id]: prev[id].map((d, i) => (i === idx ? { ...d, [field]: value } : d)),
  }));

  const confirm = () => {
    const captured = {};
    for (const line of lines) {
      captured[line.id] = (inputs[line.id] || [])
        .map((d) => ({
          imei1: (d.imei1 || '').trim(),
          imei2: (d.imei2 || '').trim(),
          serial: (d.serial || '').trim(),
          appleId: (d.appleId || '').trim(),
        }))
        .filter((d) => d.imei1 || d.imei2 || d.serial || d.appleId);
    }
    onConfirm(captured);
  };

  const num = (id, idx, field, placeholder) => (
    <input
      className="input"
      value={inputs[id][idx][field]}
      inputMode={field === 'serial' ? 'text' : 'numeric'}
      onChange={(e) => setField(id, idx, field, e.target.value)}
      placeholder={placeholder}
      style={{ fontFamily: 'monospace' }}
    />
  );

  return (
    <Modal
      title={t('IMEI / qurilma raqamlari')}
      wide
      onClose={onClose}
      footer={(
        <div className="flex gap-8" style={{ justifyContent: 'flex-end', width: '100%' }}>
          <button className="btn btn-ghost" onClick={onClose}>{t('Bekor')}</button>
          <button className="btn btn-primary" onClick={confirm}>{t("Davom etish (to'lov)")}</button>
        </div>
      )}
    >
      <p className="muted" style={{ marginBottom: 10 }}>
        {t('Har bir qurilmaning IMEI/seriya raqamini kiriting. Mijozga bog‘lab saqlanadi. Raqam qo‘l ostida bo‘lmasa, bo‘sh qoldirib davom etsa ham bo‘ladi.')}
      </p>
      {lines.map((line) => (
        <div key={line.id} className="card" style={{ marginBottom: 12 }}>
          <div className="card-head"><h2 style={{ fontSize: 14 }}>{line.name}</h2></div>
          <div className="card-pad list-stack">
            {(inputs[line.id] || []).map((_, idx) => (
              <div key={idx}>
                {line.qty > 1 && (
                  <div className="field-hint" style={{ marginBottom: 4 }}>
                    {t('Dona')} #{idx + 1}
                  </div>
                )}
                <div className="form-row">
                  {num(line.id, idx, 'imei1', t('IMEI 1 (15 raqam)'))}
                  {num(line.id, idx, 'imei2', t('IMEI 2 (ixtiyoriy)'))}
                </div>
                <div style={{ marginTop: 6 }}>
                  {num(line.id, idx, 'serial', t('Seriya raqami (S/N, ixtiyoriy)'))}
                </div>
                <div style={{ marginTop: 6 }}>
                  <input
                    className="input"
                    value={inputs[line.id][idx].appleId}
                    inputMode="email"
                    onChange={(e) => setField(line.id, idx, 'appleId', e.target.value)}
                    placeholder={t('Apple ID / iCloud — faqat iPhone uchun (ixtiyoriy)')}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}
    </Modal>
  );
}
