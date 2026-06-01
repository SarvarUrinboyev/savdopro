import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { SupplierApi } from '../api/endpoints.js';
import { ConfirmDialog, Modal } from '../components/Modal.jsx';
import { PhoneInput } from '../components/PhoneInput.jsx';
import { useToast } from '../components/Toast.jsx';
import { EmptyState, Loader, MetricCard, PageHeader } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { usd } from '../lib/format.js';

/**
 * Yetkazib beruvchilar (Suppliers): contact directory + how much we've
 * already paid each one (sum of OUTGOING SUPPLIER-category payments).
 */
export function Suppliers() {
  const navigate = useNavigate();
  const t = useT();
  const { data, loading, error, reload } = useApi(() => SupplierApi.list(), []);
  const [search, setSearch] = useState('');
  const [modal, setModal] = useState(null);
  const toast = useToast();

  const list = data || [];
  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return list;
    return list.filter((s) =>
      s.name.toLowerCase().includes(q)
      || (s.phone && s.phone.toLowerCase().includes(q)));
  }, [list, search]);

  const summary = useMemo(() => {
    const paidSum = list.reduce((s, x) => s + Number(x.paidTotal || 0), 0);
    return { count: list.length, paidSum };
  }, [list]);

  const confirmDelete = async () => {
    try {
      await SupplierApi.remove(modal.item.id);
      toast.success(t("Yetkazib beruvchi o'chirildi"));
      setModal(null);
      reload();
    } catch (err) {
      toast.error(err.message);
    }
  };

  return (
    <>
      <PageHeader
        title={t('Yetkazib beruvchilar')}
        desc={t('Yetkazib beruvchilar kontaktlari, oldindan to\'langan mablag\'lar')}
      >
        <button className="btn btn-primary" onClick={() => setModal({ type: 'add' })}>
          + {t('Yangi yetkazib beruvchi')}
        </button>
      </PageHeader>

      <div className="metrics section">
        <MetricCard tone="blue" icon="🏭" label={t('Jami yetkazib beruvchilar')}
                    value={summary.count} currency={false} />
        <MetricCard tone="green" icon="💸" label={t("Ularga to'langan")}
                    value={summary.paidSum} />
      </div>

      <div className="card section">
        <div className="card-pad">
          <div className="field" style={{ margin: 0 }}>
            <label>{t('Qidiruv')}</label>
            <input
              className="input"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={t('Ism yoki telefon raqami...')}
            />
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-head">
          <h2>{t("Yetkazib beruvchilar ro'yxati")}</h2>
          <span className="hint">{filtered.length} {t('ta')}</span>
        </div>
        <Loader loading={loading} error={error} onRetry={reload}>
          {filtered.length === 0 ? (
            <EmptyState icon="🏭" text={t("Hali yetkazib beruvchi yo'q")} />
          ) : (
            <div className="table-wrap">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>{t('Ism')}</th>
                    <th>{t('Telefon')}</th>
                    <th>{t('Manzil')}</th>
                    <th className="num">{t("To'langan")}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((s) => (
                    <tr
                      key={s.id}
                      style={{ cursor: 'pointer' }}
                      onClick={() => navigate(`/suppliers/${s.id}`)}
                    >
                      <td className="name-cell">{s.name}</td>
                      <td className="faint mono">{s.phone || '—'}</td>
                      <td className="faint">{s.address || '—'}</td>
                      <td className="num mono">{usd(s.paidTotal)}</td>
                      <td onClick={(e) => e.stopPropagation()}>
                        <div className="row-actions">
                          <button
                            className="icon-btn"
                            title={t('Tahrirlash')}
                            onClick={() => setModal({ type: 'edit', item: s })}
                          >
                            ✏️
                          </button>
                          <button
                            className="icon-btn danger"
                            title={t("O'chirish")}
                            onClick={() => setModal({ type: 'delete', item: s })}
                          >
                            🗑
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Loader>
      </div>

      {(modal?.type === 'add' || modal?.type === 'edit') && (
        <SupplierFormModal
          initial={modal.type === 'edit' ? modal.item : null}
          onSubmit={async (body) => {
            if (modal.type === 'add') {
              await SupplierApi.create(body);
              toast.success(t("Yetkazib beruvchi qo'shildi"));
            } else {
              await SupplierApi.update(modal.item.id, body);
              toast.success(t('Yangilandi'));
            }
            reload();
          }}
          onClose={() => setModal(null)}
        />
      )}

      {modal?.type === 'delete' && (
        <ConfirmDialog
          title={t("Yetkazib beruvchini o'chirish")}
          message={t("Bu yetkazib beruvchini o'chirmoqchimisiz? To'lov tarixi saqlanadi.")}
          confirmLabel={t("O'chirish")}
          onConfirm={confirmDelete}
          onCancel={() => setModal(null)}
        />
      )}
    </>
  );
}

/** Exported so SupplierDetail can reuse the same form. */
export function SupplierFormModal({ initial, onSubmit, onClose }) {
  const t = useT();
  const [name, setName] = useState(initial?.name ?? '');
  const [phone, setPhone] = useState(initial?.phone ?? '');
  const [address, setAddress] = useState(initial?.address ?? '');
  const [note, setNote] = useState(initial?.note ?? '');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    if (!name.trim()) {
      setError(t('Yetkazib beruvchi ismi kiritilishi shart'));
      return;
    }
    setBusy(true);
    try {
      await onSubmit({
        name: name.trim(),
        phone: phone.trim() || null,
        address: address.trim() || null,
        note: note.trim() || null,
      });
      onClose();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <Modal
      title={initial ? t('Yetkazib beruvchini tahrirlash') : t('Yangi yetkazib beruvchi')}
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
        <label>{t('Ism *')}</label>
        <input className="input" autoFocus value={name}
               onChange={(e) => setName(e.target.value)}
               placeholder={t('Yetkazib beruvchi ismi yoki kompaniya')} />
      </div>
      <div className="field">
        <label>{t('Telefon')}</label>
        <PhoneInput value={phone} onChange={setPhone} />
      </div>
      <div className="field">
        <label>{t('Manzil')}</label>
        <input className="input" value={address}
               onChange={(e) => setAddress(e.target.value)}
               placeholder={t("Shahar, ko'cha, uy")} />
      </div>
      <div className="field">
        <label>{t('Izoh (ixtiyoriy)')}</label>
        <input className="input" value={note}
               onChange={(e) => setNote(e.target.value)} />
      </div>
      {error && <div style={{ color: 'var(--red)', fontSize: 12 }}>{error}</div>}
    </Modal>
  );
}
