import { useState } from 'react';
import { PrintApi, ShopApi } from '../api/endpoints.js';
import { IS_WEB } from '../config.js';
import { ConfirmDialog, Modal } from '../components/Modal.jsx';
import { useToast } from '../components/Toast.jsx';
import {
  EmptyState, Loader, PageHeader,
} from '../components/ui.jsx';
import { useAuth } from '../context/Auth.jsx';
import { useT } from '../context/Settings.jsx';
import { useShop } from '../context/Shop.jsx';
import { useApi } from '../hooks/useApi.js';

/**
 * Shops management — account owner can add new shops, rename, mark the
 * main shop and delete (non-main) ones. Hidden from regular shop users
 * (the App.jsx route check redirects them away).
 */
export function Shops() {
  const t = useT();
  const toast = useToast();
  const { user } = useAuth();
  const { reload: reloadShops } = useShop();
  const { data, loading, error, reload } = useApi(() => ShopApi.list(), []);
  const [modal, setModal] = useState(null);

  const shops = data || [];
  const isOwner = user?.role === 'ACCOUNT_OWNER' || user?.role === 'SUPER_ADMIN';

  const refresh = async () => {
    await reload();
    await reloadShops();
  };

  const setMain = async (s) => {
    if (s.main) return;
    try {
      await ShopApi.setMain(s.id);
      toast.success(t("Asosiy do'kon o'zgartirildi"));
      await refresh();
    } catch (err) {
      toast.error(err.message);
    }
  };

  const confirmDelete = async () => {
    try {
      await ShopApi.remove(modal.item.id);
      toast.success(t("Do'kon o'chirildi"));
      setModal(null);
      await refresh();
    } catch (err) {
      toast.error(err.message);
    }
  };

  return (
    <>
      <PageHeader
        title={t("Do'konlar")}
        desc={t("Akkauntingizdagi do'konlarni boshqarish")}
      >
        {isOwner && (
          <button className="btn btn-primary" onClick={() => setModal({ type: 'create' })}>
            + {t("Yangi do'kon")}
          </button>
        )}
      </PageHeader>

      <Loader loading={loading} error={error} onRetry={reload}>
        {shops.length === 0 ? (
          <EmptyState icon="🏪" text={t("Do'kon yo'q")} />
        ) : (
          <div className="grid grid-2 section">
            {shops.map((s) => (
              <div key={s.id} className={`shop-card ${s.main ? 'main' : ''}`}>
                <div className="shop-card-head">
                  <div>
                    <h3>🏪 {s.name}</h3>
                    {s.address && <div className="shop-addr">{s.address}</div>}
                    {s.contactPhone && (
                      <div className="shop-phone mono">{s.contactPhone}</div>
                    )}
                  </div>
                  {s.main && (
                    <span className="shop-main-badge">{t('ASOSIY')}</span>
                  )}
                </div>
                {isOwner && (
                  <div className="shop-actions">
                    {!s.main && (
                      <button className="btn-debt outline" onClick={() => setMain(s)}>
                        ⭐ {t('Asosiy qil')}
                      </button>
                    )}
                    <button
                      className="btn-debt icon"
                      title={t('Tahrirlash')}
                      onClick={() => setModal({ type: 'edit', item: s })}
                    >✏️</button>
                    <button
                      className="btn-debt icon danger"
                      title={t("O'chirish")}
                      disabled={s.main}
                      onClick={() => setModal({ type: 'delete', item: s })}
                    >🗑</button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </Loader>

      <div className="card section">
        <div className="card-pad">
          <div className="hint" style={{ fontSize: 12 }}>
            ℹ️ {t("Hozir do'kon switcher faollashtirildi va har bir do'kon alohida sub-tenant sifatida saqlanmoqda. Keyingi versiyada har do'kon o'z mahsuloti, mijozi, kassasi bilan to'liq ajratiladi.")}
          </div>
        </div>
      </div>

      {modal?.type === 'create' && (
        <ShopFormModal
          title={t("Yangi do'kon")}
          onSubmit={async (body) => {
            await ShopApi.create(body);
            toast.success(t("Do'kon yaratildi"));
            await refresh();
          }}
          onClose={() => setModal(null)}
        />
      )}

      {modal?.type === 'edit' && (
        <ShopFormModal
          title={t("Do'konni tahrirlash")}
          initial={modal.item}
          onSubmit={async (body) => {
            await ShopApi.update(modal.item.id, body);
            toast.success(t("Do'kon yangilandi"));
            await refresh();
          }}
          onClose={() => setModal(null)}
        />
      )}

      {modal?.type === 'delete' && (
        <ConfirmDialog
          title={t("Do'konni o'chirish")}
          message={t("Ushbu do'konni o'chirmoqchimisiz?")}
          confirmLabel={t("O'chirish")}
          onConfirm={confirmDelete}
          onCancel={() => setModal(null)}
        />
      )}
    </>
  );
}

function ShopFormModal({ title, initial, onSubmit, onClose }) {
  const t = useT();
  const toast = useToast();
  const [name, setName] = useState(initial?.name || '');
  const [address, setAddress] = useState(initial?.address || '');
  const [phone, setPhone] = useState(initial?.contactPhone || '');
  // Phase 3.3 register-profile fields. Optional everywhere — empty
  // strings serialise to null on the server side so existing
  // single-shop installs keep working without touching them.
  const [printerName, setPrinterName] = useState(initial?.printerName || '');
  const [cashRegisterNo, setCashRegisterNo] = useState(initial?.cashRegisterNo || '');
  const [receiptFooter, setReceiptFooter] = useState(initial?.receiptFooter || '');
  const [printers, setPrinters] = useState(null);   // null = not loaded yet
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [testing, setTesting] = useState(false);

  // Lazy-load installed printers when the user opens the printer
  // section the first time — avoids hitting javax.print on every
  // single-shop install that doesn't even own a thermal printer.
  const ensurePrintersLoaded = async () => {
    if (printers !== null) return;
    try {
      const list = await PrintApi.listPrinters();
      setPrinters(list || []);
    } catch (err) {
      setPrinters([]);
      toast.error(err.message);
    }
  };

  const runTest = async () => {
    setTesting(true);
    try {
      const res = await PrintApi.test();
      toast.success(`${t('Sinov chop etildi')}: ${res.printer}`);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setTesting(false);
    }
  };

  const submit = async () => {
    if (!name.trim()) {
      setError(t("Do'kon nomi kiritilishi shart"));
      return;
    }
    setBusy(true);
    try {
      await onSubmit({
        name: name.trim(),
        address: address.trim() || null,
        contactPhone: phone.trim() || null,
        printerName: printerName.trim() || null,
        cashRegisterNo: cashRegisterNo.trim() || null,
        receiptFooter: receiptFooter.trim() || null,
      });
      onClose();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <Modal
      title={title}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={busy}>
            {t('Bekor qilish')}
          </button>
          <button className="btn btn-primary" onClick={submit} disabled={busy}>
            {busy ? t('Saqlanmoqda...') : t('Saqlash')}
          </button>
        </>
      }
    >
      <div className="field">
        <label>{t("Do'kon nomi *")}</label>
        <input className="input" autoFocus value={name}
               onChange={(e) => setName(e.target.value)}
               placeholder="Chilonzor filiali" />
      </div>
      <div className="field">
        <label>{t('Manzil')}</label>
        <input className="input" value={address}
               onChange={(e) => setAddress(e.target.value)} />
      </div>
      <div className="field">
        <label>{t('Telefon')}</label>
        <input className="input" value={phone}
               onChange={(e) => setPhone(e.target.value)} />
      </div>
      {/* Phase 3.3: per-shop register profile. Collapsible header so
          single-shop users don't see noise; multi-shop owners can
          configure each location's printer and cash register. */}
      <details style={{ marginTop: 12, paddingTop: 8,
                        borderTop: '1px solid var(--border)' }}
               onToggle={(e) => { if (!IS_WEB && e.target.open) ensurePrintersLoaded(); }}>
        <summary style={{ cursor: 'pointer', fontWeight: 600,
                          fontSize: 13, color: 'var(--muted)' }}>
          {IS_WEB ? '🧾' : '🖨'} {t(IS_WEB ? 'Kassa va chek sozlamalari' : 'Kassa va printer sozlamalari')}
        </summary>
        {/* Local thermal-printer config is desktop-only: the PrintApi bridge
            reaches the user's OS printer, which a hosted web backend cannot.
            printerName is still preserved (kept in state + the save payload). */}
        {!IS_WEB && (
        <div className="field" style={{ marginTop: 12 }}>
          <label>{t('Printer (Windowsda o\'rnatilgan)')}</label>
          {/* Dropdown lists what the OS sees; the free-text field below
              stays available for when the printer was just plugged in
              and the list hasn't refreshed yet, or for shared printers. */}
          {printers === null ? (
            <input className="input" value={printerName}
                   onChange={(e) => setPrinterName(e.target.value)}
                   placeholder={t('Yuklanmoqda...')} disabled />
          ) : (
            <>
              <select className="input" value={printerName}
                      onChange={(e) => setPrinterName(e.target.value)}>
                <option value="">— {t('OS standart printeri')} —</option>
                {printers.map((p) => (
                  <option key={p} value={p}>{p}</option>
                ))}
                {/* Show the saved name even if it's not in the current
                    OS list (e.g. printer is offline). */}
                {printerName && !printers.includes(printerName) && (
                  <option value={printerName}>{printerName} ({t('topilmadi')})</option>
                )}
              </select>
              <button type="button" className="btn btn-ghost btn-sm"
                      style={{ marginTop: 6 }}
                      disabled={testing} onClick={runTest}>
                {testing ? t('Chop etilmoqda...') : `🧾 ${t("Sinov sahifasini chop etish")}`}
              </button>
            </>
          )}
        </div>
        )}
        <div className="field">
          <label>{t('Kassa raqami')}</label>
          <input className="input" value={cashRegisterNo}
                 onChange={(e) => setCashRegisterNo(e.target.value)}
                 placeholder="01" />
        </div>
        <div className="field">
          <label>{t('Chek pastki yozuvi')}</label>
          <input className="input" value={receiptFooter}
                 onChange={(e) => setReceiptFooter(e.target.value)}
                 placeholder="@savdo_pro · qaytarish 14 kun ichida" />
        </div>
      </details>
      {error && <div style={{ color: 'var(--red)', fontSize: 12 }}>{error}</div>}
    </Modal>
  );
}
