import { useState } from 'react';
import { AccountingApi } from '../api/endpoints.js';
import { AccountingTabs } from '../components/AccountingTabs.jsx';
import { ConfirmDialog, Modal } from '../components/Modal.jsx';
import { useToast } from '../components/Toast.jsx';
import { EmptyState, Loader, PageHeader } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { formatDate, formatDateTime } from '../lib/format.js';

/** Accounting periods ("Hisobot davrlari") — close a finished month to lock it. */
export function AccountingPeriods() {
  const t = useT();
  const toast = useToast();
  const [modal, setModal] = useState(null);

  const { data, loading, error, reload } = useApi(() => AccountingApi.periods(), []);

  return (
    <>
      <PageHeader title={t('Hisobot davrlari')}
                  desc={t('Yopilgan davrga yangi yozuv kiritib bo‘lmaydi (qulflanadi)')}>
        <button className="btn btn-primary" onClick={() => setModal({ type: 'close' })}>
          🔒 {t('Davrni yopish')}
        </button>
      </PageHeader>

      <AccountingTabs />

      <div className="card">
        <div className="card-head"><h2>{t('Davrlar')}</h2></div>
        <Loader loading={loading} error={error} onRetry={reload}>
          {!data || data.length === 0 ? (
            <EmptyState icon="🗓" text={t('Hali davr yopilmagan')} />
          ) : (
            <div className="table-wrap">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>{t('Davr')}</th>
                    <th>{t('Holat')}</th>
                    <th>{t('Yopilgan vaqt')}</th>
                    <th>{t('Izoh')}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.map((p) => (
                    <tr key={p.id}>
                      <td className="nowrap">{formatDate(p.periodStart)} — {formatDate(p.periodEnd)}</td>
                      <td>
                        <span className={`badge ${p.status === 'CLOSED' ? 'badge-qarzga' : 'badge-naqd'}`}>
                          {p.status === 'CLOSED' ? t('Yopiq') : t('Ochiq')}
                        </span>
                      </td>
                      <td className="faint nowrap">
                        {p.closedAt ? formatDateTime(p.closedAt) : '—'}
                        {p.closedBy ? ` · ${p.closedBy}` : ''}
                      </td>
                      <td className="faint">{p.note || '—'}</td>
                      <td className="right">
                        <div className="row-actions">
                          {p.status === 'CLOSED' && (
                            <button className="btn btn-ghost btn-sm"
                                    onClick={() => setModal({ type: 'reopen', item: p })}>
                              🔓 {t('Ochish')}
                            </button>
                          )}
                          <button className="icon-btn danger" title={t("O'chirish")}
                                  onClick={() => setModal({ type: 'delete', item: p })}>🗑</button>
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

      {modal?.type === 'close' && (
        <ClosePeriodModal
          onSubmit={async (body) => {
            await AccountingApi.closePeriod(body);
            toast.success(t('Davr yopildi'));
            reload();
          }}
          onClose={() => setModal(null)}
        />
      )}

      {modal?.type === 'reopen' && (
        <ConfirmDialog
          title={t('Davrni ochish')}
          message={t('Bu davrni qayta ochmoqchimisiz? Unga yozuv kiritish mumkin bo‘ladi.')}
          confirmLabel={t('Ochish')}
          confirmTone="btn-primary"
          onConfirm={async () => {
            try {
              await AccountingApi.reopenPeriod(modal.item.id);
              toast.success(t('Davr ochildi'));
              setModal(null); reload();
            } catch (err) { toast.error(err.message); }
          }}
          onCancel={() => setModal(null)}
        />
      )}

      {modal?.type === 'delete' && (
        <ConfirmDialog
          title={t("Davrni o'chirish")}
          message={t("Davr yozuvi o'chiriladi (yozuvlarning o'ziga tegmaydi). Davom etamizmi?")}
          confirmLabel={t("O'chirish")}
          onConfirm={async () => {
            try {
              await AccountingApi.removePeriod(modal.item.id);
              toast.success(t("Davr o'chirildi"));
              setModal(null); reload();
            } catch (err) { toast.error(err.message); }
          }}
          onCancel={() => setModal(null)}
        />
      )}
    </>
  );
}

function ClosePeriodModal({ onSubmit, onClose }) {
  const t = useT();
  const now = new Date();
  const defMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  const [month, setMonth] = useState(defMonth);
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    if (!month) { setError(t('Oy tanlanishi shart')); return; }
    const [y, m] = month.split('-').map(Number);
    const lastDay = new Date(y, m, 0).getDate();
    const start = `${month}-01`;
    const end = `${month}-${String(lastDay).padStart(2, '0')}`;
    setBusy(true);
    try {
      await onSubmit({ periodStart: start, periodEnd: end, note: note.trim() || null });
      onClose();
    } catch (err) { setError(err.message); setBusy(false); }
  };

  return (
    <Modal
      title={t('Davrni yopish')}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={busy}>{t('Bekor qilish')}</button>
          <button className="btn btn-primary" onClick={submit} disabled={busy}>
            {busy ? t('Yopilmoqda...') : t('Yopish')}
          </button>
        </>
      }
    >
      <div className="field">
        <label>{t('Oy')}</label>
        <input className="input" type="month" value={month}
               onChange={(e) => setMonth(e.target.value)} />
      </div>
      <div className="field">
        <label>{t('Izoh (ixtiyoriy)')}</label>
        <input className="input" value={note} onChange={(e) => setNote(e.target.value)}
               placeholder={t('Masalan: iyun oyi yopildi')} />
      </div>
      <div className="faint" style={{ fontSize: 12 }}>
        {t('Yopilgandan keyin bu oyga tegishli har qanday yozuv bloklanadi.')}
      </div>
      {error && <div style={{ color: 'var(--red)', fontSize: 12, marginTop: 8 }}>{error}</div>}
    </Modal>
  );
}
