import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AdminApi } from '../api/endpoints.js';
import { Modal } from '../components/Modal.jsx';
import { useToast } from '../components/Toast.jsx';
import { Loader, PageHeader } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { ALL_MODULES, modulesFromCsv } from '../lib/modules.js';

// Per-user permission editor: each toggle grants a module's RESOURCE:ACTION set.
// Checking a module gives the user read+write for it; an empty override means
// "fall back to the role's defaults".
const MODULE_PERMS = {
  pos: ['SALES:READ', 'SALES:WRITE', 'PAYMENTS:READ', 'PAYMENTS:WRITE'],
  'pos-history': ['SALES:READ'],
  promos: ['PROMOS:READ', 'PROMOS:WRITE'],
  management: ['MANAGEMENT:READ', 'MANAGEMENT:WRITE'],
  'home-expenses': ['EXPENSES:READ', 'EXPENSES:WRITE'],
  payments: ['PAYMENTS:READ', 'PAYMENTS:WRITE'],
  orders: ['ORDERS:READ', 'ORDERS:WRITE'],
  warehouse: ['PRODUCTS:READ', 'PRODUCTS:WRITE'],
  customers: ['CUSTOMERS:READ', 'CUSTOMERS:WRITE'],
  suppliers: ['SUPPLIERS:READ', 'SUPPLIERS:WRITE'],
  debt: ['DEBTS:READ', 'DEBTS:WRITE'],
  reports: ['REPORTS:READ', 'REPORTS:WRITE'],
  shops: ['SHOPS:READ', 'SHOPS:WRITE'],
  transfers: ['TRANSFERS:READ', 'TRANSFERS:WRITE'],
};
const PERM_MODULES = ALL_MODULES.filter((m) => MODULE_PERMS[m.key]);

/**
 * Per-account permissions / sidebar configuration page.
 *
 * Renders the canonical list of sidebar modules with a checkbox per row;
 * the super-admin toggles each one and presses "Saqlash" to push the
 * comma-separated allow-list back to the License Server. The next time
 * any user of this account calls /me, the desktop sidebar re-renders
 * with only the enabled modules visible.
 *
 * Empty list = "all modules visible" (legacy default). To turn the
 * filter OFF entirely, click "Hammasini yoqish" then save.
 */
export function AccountDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const t = useT();
  const toast = useToast();

  const { data, loading, error, reload } =
    useApi(() => AdminApi.accountDetail(id), [id]);

  // Selected module keys — initialised once from the server payload,
  // then mutated locally until the user presses Save.
  const [selected, setSelected] = useState(null);
  const [saving, setSaving] = useState(false);
  const [grantPlan, setGrantPlan] = useState('BASIC');
  const [grantMonths, setGrantMonths] = useState(1);
  const [granting, setGranting] = useState(false);
  const [permUser, setPermUser] = useState(null); // user whose permissions are being edited

  useEffect(() => {
    if (!data) return;
    const csv = data.account?.enabledModules;
    setSelected(modulesFromCsv(csv, ALL_MODULES.map((m) => m.key)));
  }, [data]);

  if (loading || !selected) {
    return <Loader loading={true} />;
  }
  if (error) {
    return <Loader error={error} onRetry={reload} />;
  }

  const account = data.account;
  const toggle = (key) => {
    setSelected((prev) =>
      prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key]);
  };
  const enableAll = () => setSelected(ALL_MODULES.map((m) => m.key));
  const disableAll = () => setSelected([]);

  const save = async () => {
    setSaving(true);
    try {
      // Empty selection => null => "all modules visible" on the server.
      // Full selection (everything ticked) we also send as null so a
      // future module added to ALL_MODULES is automatically visible.
      const allTicked = selected.length === ALL_MODULES.length;
      const csv = allTicked || selected.length === 0
        ? null
        : selected.join(',');
      await AdminApi.setModules(account.id, csv);
      toast.success(t('Saqlandi — foydalanuvchilar keyingi kirishda yangi sozlamani ko\'radi'));
      reload();
    } catch (err) {
      toast.error(err.message || t('Saqlab bo\'lmadi'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <PageHeader
        title={account.name}
        desc={t("Sidebar modullari va sozlamalar")}
      >
        <button className="btn btn-ghost" onClick={() => navigate('/admin')}>
          ← {t('Akkauntlar ro\'yxati')}
        </button>
      </PageHeader>

      <div className="card section" style={{ padding: 16 }}>
        <h2 style={{ marginTop: 0 }}>{t('Obuna')}</h2>
        <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap', fontSize: 14, marginBottom: 12 }}>
          <div><span className="faint">{t('Tarif')}: </span><b>{account.plan}</b></div>
          <div>
            <span className="faint">{t('Holat')}: </span>
            <b style={{ color: account.blocked || account.expired ? '#dc2626' : '#16a34a' }}>
              {account.blocked ? t('Bloklangan') : account.expired ? t('Muddati tugagan') : t('Faol')}
            </b>
          </div>
          <div>
            <span className="faint">{t('Tugaydi')}: </span>
            <b>{account.subscriptionExpires || '—'}</b>
            {!account.expired && account.subscriptionExpires
              ? ` (${account.daysUntilBlock} ${t('kun')})` : ''}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <select
            className="input"
            value={grantPlan}
            onChange={(e) => setGrantPlan(e.target.value)}
            style={{ width: 'auto' }}
          >
            <option value="TRIAL">TRIAL</option>
            <option value="BASIC">BASIC</option>
            <option value="STANDARD">STANDARD</option>
            <option value="PRO">PRO</option>
          </select>
          <input
            className="input"
            type="number"
            min="1"
            value={grantMonths}
            onChange={(e) => setGrantMonths(Number(e.target.value) || 1)}
            style={{ width: 80 }}
          />
          <span className="faint">{t('oy')}</span>
          <button
            className="btn btn-primary"
            disabled={granting}
            onClick={async () => {
              setGranting(true);
              try {
                await AdminApi.grant(account.id, grantPlan, grantMonths);
                reload();
              } catch (e) {
                alert(e.message || 'Xatolik');
              } finally {
                setGranting(false);
              }
            }}
          >
            {granting ? '...' : t("Tarifni berish / uzaytirish")}
          </button>
        </div>
      </div>

      <div className="card section">
        <div className="card-head">
          <h2>{t('Modullar')}</h2>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-ghost" onClick={enableAll}>
              ✓ {t('Hammasini yoqish')}
            </button>
            <button className="btn btn-ghost" onClick={disableAll}>
              ✕ {t('Hammasini o\'chirish')}
            </button>
          </div>
        </div>

        <p className="faint" style={{ padding: '0 16px 8px', fontSize: 13 }}>
          {t("Belgilangan modullar shu akkauntning barcha foydalanuvchilari uchun chap menyuda ko'rinadi. Hech narsa belgilanmasa — hammasi ko'rinadi (legacy default).")}
        </p>

        <div className="modules-grid">
          {ALL_MODULES.map((m) => {
            const checked = selected.includes(m.key);
            return (
              <label
                key={m.key}
                className={`module-row ${checked ? 'on' : ''}`}
              >
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() => toggle(m.key)}
                />
                <div className="module-meta">
                  <div className="module-label">{t(m.label)}</div>
                  <div className="module-key">{m.key}</div>
                </div>
              </label>
            );
          })}
        </div>

        <div className="card-foot" style={{ padding: 16, display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button className="btn btn-ghost" onClick={() => navigate('/admin')}>
            {t('Bekor qilish')}
          </button>
          <button className="btn btn-primary" onClick={save} disabled={saving}>
            {saving ? t('Saqlanmoqda...') : t('Saqlash')}
          </button>
        </div>
      </div>

      <div className="card section">
        <div className="card-head">
          <h2>{t('Foydalanuvchilar')} ({data.users.length})</h2>
        </div>
        <div className="table-wrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>{t('Login')}</th>
                <th>{t('Ism')}</th>
                <th>{t('Rol')}</th>
                <th>{t('Oxirgi kirish')}</th>
                <th>{t('Ruxsat')}</th>
              </tr>
            </thead>
            <tbody>
              {data.users.map((u) => (
                <tr key={u.id}>
                  <td className="mono"><strong>{u.username}</strong></td>
                  <td>{u.fullName || '—'}</td>
                  <td><span className="badge">{u.role}</span></td>
                  <td className="faint mono">{u.lastLoginAt || '—'}</td>
                  <td>
                    <button className="btn btn-ghost" style={{ padding: '4px 10px' }}
                            onClick={() => setPermUser(u)}>
                      🔐 {t('Ruxsatlar')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
      {permUser && (
        <PermModal
          user={permUser}
          onClose={() => setPermUser(null)}
          onSaved={() => { setPermUser(null); reload(); }}
          toast={toast}
          t={t}
        />
      )}
    </>
  );
}

function PermModal({ user, onClose, onSaved, toast, t }) {
  const ownerLike = user.role === 'ACCOUNT_OWNER' || user.role === 'SUPER_ADMIN';
  // Selected module keys: derived from the user's current override CSV.
  const [sel, setSel] = useState(() => {
    const have = new Set((user.permissions || '').split(',').map((s) => s.trim()).filter(Boolean));
    if (have.has('*:*')) return new Set(PERM_MODULES.map((m) => m.key));
    return new Set(PERM_MODULES.filter((m) =>
      MODULE_PERMS[m.key].every((p) => have.has(p))).map((m) => m.key));
  });
  const [busy, setBusy] = useState(false);

  const toggle = (key) => setSel((s) => {
    const n = new Set(s);
    n.has(key) ? n.delete(key) : n.add(key);
    return n;
  });

  const save = async () => {
    const csv = [...sel].flatMap((k) => MODULE_PERMS[k]).join(',');
    setBusy(true);
    try {
      await AdminApi.setPermissions(user.id, csv || null);
      toast.success(t('Ruxsatlar saqlandi — foydalanuvchi keyingi kirishda yangilanadi'));
      onSaved();
    } catch (e) {
      toast.error(e.message);
      setBusy(false);
    }
  };

  return (
    <Modal title={`🔐 ${user.username} — ${t('ruxsatlar')}`} onClose={onClose} footer={
      <>
        <button className="btn btn-ghost" onClick={() => setSel(new Set())}>{t('Tozalash')}</button>
        <button className="btn btn-primary" onClick={save} disabled={busy}>
          {busy ? t('Saqlanmoqda...') : t('Saqlash')}
        </button>
      </>
    }>
      <p className="faint" style={{ marginTop: 0 }}>
        {t("Foydalanuvchi ko'ra/o'zgartira oladigan bo'limlarni belgilang. Hech narsa belgilanmasa — rol bo'yicha standart ruxsatlar ishlatiladi.")}
      </p>
      {ownerLike && (
        <p style={{ color: '#f59e0b', fontWeight: 600 }}>
          ⚠️ {t("Bu — akkaunt egasi. Cheklov qo'ysangiz, o'zining ba'zi bo'limlarini yo'qotadi.")}
        </p>
      )}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
        {PERM_MODULES.map((m) => (
          <label key={m.key} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 4px', cursor: 'pointer' }}>
            <input type="checkbox" checked={sel.has(m.key)} onChange={() => toggle(m.key)} />
            <span>{t(m.label)}</span>
          </label>
        ))}
      </div>
    </Modal>
  );
}
