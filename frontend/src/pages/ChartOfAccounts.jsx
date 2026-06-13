import { useState } from 'react';
import { AccountingApi } from '../api/endpoints.js';
import { AccountingTabs } from '../components/AccountingTabs.jsx';
import { ConfirmDialog, Modal } from '../components/Modal.jsx';
import { useToast } from '../components/Toast.jsx';
import { EmptyState, Loader, PageHeader } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';

const TYPES = [
  ['ASSET', 'Aktiv'],
  ['LIABILITY', 'Passiv'],
  ['EQUITY', 'Kapital'],
  ['REVENUE', 'Daromad'],
  ['EXPENSE', 'Xarajat'],
];
const TYPE_LABEL = Object.fromEntries(TYPES);
const TYPE_BADGE = {
  ASSET: 'badge-naqd', LIABILITY: 'badge-qarzga', EQUITY: 'badge-aralash',
  REVENUE: 'badge-karta', EXPENSE: 'badge-muted',
};

/** Chart of Accounts ("Hisoblar rejasi"). */
export function ChartOfAccounts() {
  const t = useT();
  const toast = useToast();
  const [modal, setModal] = useState(null);
  const [busy, setBusy] = useState(false);

  const { data, loading, error, reload } = useApi(() => AccountingApi.accounts(), []);

  const runBackfill = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const res = await AccountingApi.backfill();
      toast.success(`${t('Bosh kitob to‘ldirildi')}: ${res.created} ${t('ta yozuv')}`);
      reload();
    } catch (err) {
      toast.error(err.message);
    }
    setBusy(false);
  };

  return (
    <>
      <PageHeader title={t('Hisoblar rejasi')}
                  desc={t('Buxgalteriya hisoblari — avtomatik postingning manzili')}>
        <button className="btn btn-ghost" onClick={runBackfill} disabled={busy}
                title={t('Mavjud savdo/xarajat tarixidan Bosh kitobni to‘ldirish')}>
          {busy ? t('Bajarilmoqda...') : '↻ ' + t('Tarixdan to‘ldirish')}
        </button>
        <button className="btn btn-primary" onClick={() => setModal({ type: 'add' })}>
          + {t('Hisob qo‘shish')}
        </button>
      </PageHeader>

      <AccountingTabs />

      <div className="card">
        <div className="card-head">
          <h2>{t('Hisoblar')}</h2>
          <span className="hint">{data?.length || 0} {t('ta')}</span>
        </div>
        <Loader loading={loading} error={error} onRetry={reload}>
          {!data || data.length === 0 ? (
            <EmptyState icon="📚" text={t('Hisob topilmadi')} />
          ) : (
            <div className="table-wrap">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>{t('Kod')}</th>
                    <th>{t('Nomi')}</th>
                    <th>{t('Turi')}</th>
                    <th>{t('Normal qoldiq')}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.map((a) => (
                    <tr key={a.id} style={{ opacity: a.active ? 1 : 0.5 }}>
                      <td className="mono">{a.code}</td>
                      <td className="name-cell">
                        {a.name}
                        {a.system && <span className="badge badge-muted" style={{ marginLeft: 8 }}>
                          {t('Tizim')}</span>}
                      </td>
                      <td><span className={`badge ${TYPE_BADGE[a.type]}`}>{t(TYPE_LABEL[a.type])}</span></td>
                      <td className="faint">{a.normalBalance === 'DEBIT' ? t('Debet') : t('Kredit')}</td>
                      <td>
                        <div className="row-actions">
                          <button className="icon-btn" title={t('Tahrirlash')}
                                  onClick={() => setModal({ type: 'edit', item: a })}>✏️</button>
                          {!a.system && (
                            <button className="icon-btn danger" title={t("O'chirish")}
                                    onClick={() => setModal({ type: 'delete', item: a })}>🗑</button>
                          )}
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
        <AccountModal
          initial={modal.type === 'edit' ? modal.item : null}
          onSubmit={async (body) => {
            if (modal.type === 'add') {
              await AccountingApi.createAccount(body);
              toast.success(t('Hisob qo‘shildi'));
            } else {
              await AccountingApi.updateAccount(modal.item.id, body);
              toast.success(t('Hisob yangilandi'));
            }
            reload();
          }}
          onClose={() => setModal(null)}
        />
      )}

      {modal?.type === 'delete' && (
        <ConfirmDialog
          title={t("Hisobni o'chirish")}
          message={`"${modal.item.name}" ${t("hisobini o'chirmoqchimisiz?")}`}
          confirmLabel={t("O'chirish")}
          onConfirm={async () => {
            try {
              await AccountingApi.removeAccount(modal.item.id);
              toast.success(t("Hisob o'chirildi"));
              setModal(null);
              reload();
            } catch (err) { toast.error(err.message); }
          }}
          onCancel={() => setModal(null)}
        />
      )}
    </>
  );
}

function AccountModal({ initial, onSubmit, onClose }) {
  const t = useT();
  const [code, setCode] = useState(initial?.code ?? '');
  const [name, setName] = useState(initial?.name ?? '');
  const [type, setType] = useState(initial?.type ?? 'EXPENSE');
  const [active, setActive] = useState(initial?.active ?? true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const isSystem = initial?.system;

  const submit = async () => {
    if (!code.trim()) { setError(t('Kod kiritilishi shart')); return; }
    if (!name.trim()) { setError(t('Nomi kiritilishi shart')); return; }
    setBusy(true);
    try {
      await onSubmit({ code: code.trim(), name: name.trim(), type, active });
      onClose();
    } catch (err) { setError(err.message); setBusy(false); }
  };

  return (
    <Modal
      title={initial ? t('Hisobni tahrirlash') : t('Yangi hisob')}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={busy}>{t('Bekor qilish')}</button>
          <button className="btn btn-primary" onClick={submit} disabled={busy}>
            {busy ? t('Saqlanmoqda...') : t('Saqlash')}
          </button>
        </>
      }
    >
      <div className="form-row">
        <div className="field">
          <label>{t('Kod')} *</label>
          <input className="input mono" value={code} disabled={isSystem}
                 onChange={(e) => setCode(e.target.value)} placeholder="6500" />
        </div>
        <div className="field">
          <label>{t('Turi')}</label>
          <select className="select" value={type} disabled={isSystem}
                  onChange={(e) => setType(e.target.value)}>
            {TYPES.map(([k, l]) => <option key={k} value={k}>{t(l)}</option>)}
          </select>
        </div>
      </div>
      <div className="field">
        <label>{t('Nomi')} *</label>
        <input className="input" autoFocus value={name}
               onChange={(e) => setName(e.target.value)} placeholder={t('Hisob nomi')} />
      </div>
      <label className="flex" style={{ gap: 8, alignItems: 'center', cursor: 'pointer' }}>
        <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
        <span>{t('Faol')}</span>
      </label>
      {isSystem && <div className="faint" style={{ fontSize: 12, marginTop: 8 }}>
        {t('Tizim hisobining kodi va turi o‘zgartirilmaydi')}</div>}
      {error && <div style={{ color: 'var(--red)', fontSize: 12, marginTop: 8 }}>{error}</div>}
    </Modal>
  );
}
