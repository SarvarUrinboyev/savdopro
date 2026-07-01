import { useState } from 'react';
import { PromoApi } from '../api/endpoints.js';
import { ConfirmDialog, Modal } from '../components/Modal.jsx';
import { useToast } from '../components/Toast.jsx';
import { EmptyState, Loader, PageHeader } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { money, todayIso } from '../lib/format.js';

const KIND_LABELS = {
  PERCENT_OFF: '% chegirma',
  AMOUNT_OFF: 'Summa chegirma',
  BOGO: 'Buy-One-Get-One',
};

/** Order matches the backend bitmask: Mon = bit 0 … Sun = bit 6. */
const WEEKDAYS = ['Du', 'Se', 'Cho', 'Pa', 'Ju', 'Sha', 'Ya'];

/** "Du–Ya" for every day, else the checked days ("Du, Ju, Sha"). */
function weekdaysLabel(mask) {
  const m = mask ?? 127;
  if ((m & 127) === 127) return null; // every day — not worth a badge
  const names = WEEKDAYS.filter((_, i) => m & (1 << i));
  return names.length ? names.join(', ') : '—';
}

/**
 * Promo campaign admin — list / create / edit / delete.
 * POS auto-applies the best matching active campaign at checkout.
 */
export function Promos() {
  const t = useT();
  const toast = useToast();
  const { data, loading, error, reload } = useApi(() => PromoApi.list(), []);
  const [modal, setModal] = useState(null);
  const rows = data || [];

  const onSave = async (body) => {
    try {
      if (body.id) await PromoApi.update(body.id, body);
      else await PromoApi.create(body);
      toast.success(t('Saqlandi'));
      reload();
      setModal(null);
    } catch (err) {
      toast.error(err.message);
    }
  };

  const onDelete = async (id) => {
    try {
      await PromoApi.remove(id);
      toast.success(t("O'chirildi"));
      reload();
      setModal(null);
    } catch (err) {
      toast.error(err.message);
    }
  };

  return (
    <>
      <PageHeader title={t('Aksiyalar')} desc={t('Chegirma kampaniyalari — POS avtomatik qo\'llaydi')}>
        <button className="btn btn-primary" onClick={() => setModal({ type: 'edit', item: emptyPromo() })}>
          + {t('Yangi aksiya')}
        </button>
      </PageHeader>

      <div className="card section">
        <Loader loading={loading} error={error} onRetry={reload}>
          {rows.length === 0 ? (
            <EmptyState icon="🎁" text={t('Hozircha aksiya yo\'q')} />
          ) : (
            <div className="table-wrap">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>{t('Nomi')}</th>
                    <th>{t('Turi')}</th>
                    <th className="num">{t('Qiymati')}</th>
                    <th>{t('Davr')}</th>
                    <th>{t('Holat')}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {rows.map((p) => (
                    <tr key={p.id}>
                      <td><strong>{p.name}</strong>
                        {p.description && <div className="faint" style={{ fontSize: 12 }}>{p.description}</div>}
                      </td>
                      <td><span className="badge">{t(KIND_LABELS[p.kind] || p.kind)}</span></td>
                      <td className="num mono">
                        {p.kind === 'PERCENT_OFF' && `${p.valuePercent}%`}
                        {p.kind === 'AMOUNT_OFF' && `${money(p.valueAmount)} so'm`}
                        {p.kind === 'BOGO' && `${p.buyQty}+${p.getQty}`}
                      </td>
                      <td className="faint mono" style={{ fontSize: 12 }}>
                        {p.startsAt?.slice(0, 10)} — {p.endsAt?.slice(0, 10)}
                        {weekdaysLabel(p.weekdayMask) && (
                          <div style={{ fontSize: 11 }}>📅 {weekdaysLabel(p.weekdayMask)}</div>
                        )}
                      </td>
                      <td>
                        {p.active
                          ? <span className="badge badge-naqd">{t('Faol')}</span>
                          : <span className="badge">{t('O\'chirilgan')}</span>}
                      </td>
                      <td>
                        <button className="icon-btn" onClick={() => setModal({ type: 'edit', item: p })}>✏️</button>
                        <button className="icon-btn danger" onClick={() => setModal({ type: 'delete', item: p })}>🗑</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Loader>
      </div>

      {modal?.type === 'edit' && (
        <PromoFormModal item={modal.item} onSubmit={onSave} onClose={() => setModal(null)} />
      )}
      {modal?.type === 'delete' && (
        <ConfirmDialog
          title={t('Aksiyani o\'chirish?')}
          message={modal.item.name}
          onConfirm={() => onDelete(modal.item.id)}
          onClose={() => setModal(null)}
        />
      )}
    </>
  );
}

function emptyPromo() {
  return {
    name: '', kind: 'PERCENT_OFF',
    valuePercent: 10, valueAmount: 0, minSubtotalUzs: 0,
    buyQty: 1, getQty: 1, productId: null, categoryId: null,
    startsAt: todayIso() + 'T00:00:00',
    endsAt: addDays(todayIso(), 30) + 'T23:59:59',
    active: true, weekdayMask: 127, description: '',
  };
}
function addDays(iso, days) {
  const d = new Date(iso);
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

function PromoFormModal({ item, onSubmit, onClose }) {
  const t = useT();
  const [form, setForm] = useState(item);
  const set = (k, v) => setForm((p) => ({ ...p, [k]: v }));

  return (
    <Modal title={item.id ? t('Aksiyani tahrirlash') : t('Yangi aksiya')} onClose={onClose} size="md">
      <div className="field">
        <label>{t('Nomi')}</label>
        <input className="input" value={form.name} onChange={(e) => set('name', e.target.value)} />
      </div>
      <div className="field">
        <label>{t('Turi')}</label>
        <select className="input" value={form.kind} onChange={(e) => set('kind', e.target.value)}>
          <option value="PERCENT_OFF">{t('% chegirma')}</option>
          <option value="AMOUNT_OFF">{t('Summa chegirma')}</option>
          <option value="BOGO">{t('Buy-One-Get-One')}</option>
        </select>
      </div>
      {form.kind === 'PERCENT_OFF' && (
        <div className="field">
          <label>{t('Chegirma %')}</label>
          <input type="number" className="input" min="0" max="100"
            value={form.valuePercent} onChange={(e) => set('valuePercent', Number(e.target.value))} />
        </div>
      )}
      {form.kind === 'AMOUNT_OFF' && (
        <>
          <div className="field">
            <label>{t('Chegirma summa (UZS)')}</label>
            <input type="number" className="input"
              value={form.valueAmount} onChange={(e) => set('valueAmount', Number(e.target.value))} />
          </div>
          <div className="field">
            <label>{t('Minimum savatcha summasi (UZS)')}</label>
            <input type="number" className="input"
              value={form.minSubtotalUzs} onChange={(e) => set('minSubtotalUzs', Number(e.target.value))} />
          </div>
        </>
      )}
      {form.kind === 'BOGO' && (
        <>
          <div className="field">
            <label>{t('Mahsulot ID (BOGO uchun majburiy)')}</label>
            <input type="number" className="input"
              value={form.productId || ''} onChange={(e) => set('productId', Number(e.target.value) || null)} />
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <div className="field" style={{ flex: 1 }}>
              <label>{t('Buy N')}</label>
              <input type="number" className="input" min="1"
                value={form.buyQty} onChange={(e) => set('buyQty', Number(e.target.value))} />
            </div>
            <div className="field" style={{ flex: 1 }}>
              <label>{t('Get M')}</label>
              <input type="number" className="input" min="1"
                value={form.getQty} onChange={(e) => set('getQty', Number(e.target.value))} />
            </div>
          </div>
        </>
      )}
      <div style={{ display: 'flex', gap: 8 }}>
        <div className="field" style={{ flex: 1 }}>
          <label>{t('Boshlanadi')}</label>
          <input type="datetime-local" className="input"
            value={(form.startsAt || '').slice(0, 16)}
            onChange={(e) => set('startsAt', e.target.value)} />
        </div>
        <div className="field" style={{ flex: 1 }}>
          <label>{t('Tugaydi')}</label>
          <input type="datetime-local" className="input"
            value={(form.endsAt || '').slice(0, 16)}
            onChange={(e) => set('endsAt', e.target.value)} />
        </div>
      </div>
      <div className="field">
        <label>{t('Hafta kunlari')} <span className="faint">({t('aksiya faqat belgilangan kunlarda qo\'llanadi')})</span></label>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {WEEKDAYS.map((d, i) => {
            const bit = 1 << i; // backend: Mon = bit 0 (PromoService.findBest)
            const on = (form.weekdayMask ?? 127) & bit;
            return (
              <button key={d} type="button"
                onClick={() => set('weekdayMask', (form.weekdayMask ?? 127) ^ bit)}
                style={{
                  padding: '5px 10px', borderRadius: 8, fontSize: 12, fontWeight: 700,
                  cursor: 'pointer',
                  border: on ? '1px solid var(--brand-primary, #3b82f6)' : '1px solid var(--border, #d1d5db)',
                  background: on ? 'var(--brand-primary, #3b82f6)' : 'transparent',
                  color: on ? '#fff' : 'inherit',
                }}>
                {t(d)}
              </button>
            );
          })}
        </div>
        {((form.weekdayMask ?? 127) === 0) && (
          <div style={{ color: '#dc2626', fontSize: 12, marginTop: 4 }}>
            {t('Hech bir kun tanlanmagan — aksiya hech qachon qo\'llanmaydi')}
          </div>
        )}
      </div>
      <div className="field">
        <label>{t('Tavsif (ixtiyoriy)')}</label>
        <input className="input" value={form.description || ''} onChange={(e) => set('description', e.target.value)} />
      </div>
      <label style={{ display: 'flex', gap: 8, alignItems: 'center', margin: '8px 0' }}>
        <input type="checkbox" checked={form.active} onChange={(e) => set('active', e.target.checked)} />
        <span>{t('Faol')}</span>
      </label>
      <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
        <button className="btn btn-ghost" onClick={onClose}>{t('Bekor qilish')}</button>
        <button className="btn btn-primary" onClick={() => onSubmit(form)}>{t('Saqlash')}</button>
      </div>
    </Modal>
  );
}
