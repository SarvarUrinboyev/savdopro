import { useState } from 'react';
import { ProductApi } from '../api/endpoints.js';
import { downloadAuthed } from '../lib/download.js';
import { useT } from '../context/Settings.jsx';
import { Modal } from './Modal.jsx';
import { useToast } from './Toast.jsx';

/** CSV / XLSX bulk-import dialog for warehouse products. */
export function ImportModal({ onClose, onDone }) {
  const [file, setFile] = useState(null);
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);
  const [tplBusy, setTplBusy] = useState(false);
  const toast = useToast();
  const t = useT();

  const runImport = async () => {
    if (!file) {
      toast.error(t('Avval faylni tanlang'));
      return;
    }
    setBusy(true);
    try {
      const outcome = await ProductApi.importFile(file);
      setResult(outcome);
      if (outcome.importedCount > 0) {
        toast.success(`${outcome.importedCount} ${t('ta mahsulot import qilindi')}`);
        onDone();
      } else {
        toast.error(t('Hech qanday mahsulot import qilinmadi'));
      }
    } catch (err) {
      toast.error(err.message);
    }
    setBusy(false);
  };

  return (
    <Modal
      title={t('CSV / XLSX import')}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={busy}>
            {t('Yopish')}
          </button>
          <button className="btn btn-primary" onClick={runImport} disabled={busy || !file}>
            {busy ? t('Yuklanmoqda...') : t('Import qilish')}
          </button>
        </>
      }
    >
      <p className="muted" style={{ marginBottom: 12 }}>
        {t('Excel (.xlsx) yoki CSV fayl yuklang. Ustunlar:')}{' '}
        <b>{t('Nomi, IMEI 1, IMEI 2, Kelish narxi, Sotilish narxi, Miqdor, Toifa')}</b>.
      </p>
      <button
        type="button"
        className="btn btn-ghost btn-sm"
        style={{ marginBottom: 14 }}
        disabled={tplBusy}
        onClick={async () => {
          setTplBusy(true);
          try {
            await downloadAuthed(ProductApi.templateUrl, 'namuna-shablon.xlsx');
          } catch (err) {
            toast.error(err.message);
          }
          setTplBusy(false);
        }}
      >
        ⬇ {tplBusy ? t('Yuklanmoqda...') : t('Namuna shablonni yuklab olish')}
      </button>
      <div className="field" style={{ marginTop: 14 }}>
        <label>{t('Fayl')}</label>
        <input
          className="input"
          type="file"
          accept=".csv,.xlsx,.xls"
          onChange={(e) => {
            setFile(e.target.files[0] || null);
            setResult(null);
          }}
        />
      </div>

      {result && (
        <div className="card card-pad mt-8">
          <div>
            ✅ {t('Import qilindi:')} <b>{result.importedCount}</b> {t('ta')}
          </div>
          {result.skippedCount > 0 && (
            <div className="mt-8">
              ⚠️ {t("O'tkazib yuborildi:")} <b>{result.skippedCount}</b> {t('ta')}
            </div>
          )}
          {result.errors && result.errors.length > 0 && (
            <ul className="faint" style={{ fontSize: 12, marginTop: 8, paddingLeft: 18 }}>
              {result.errors.slice(0, 12).map((e, i) => (
                <li key={i}>{e}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Modal>
  );
}
