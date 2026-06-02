import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AdminApi } from '../api/endpoints.js';
import { ConfirmDialog, Modal } from '../components/Modal.jsx';
import { PhoneInput } from '../components/PhoneInput.jsx';
import { useToast } from '../components/Toast.jsx';
import {
  EmptyState, Loader, PageHeader,
} from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { formatDate } from '../lib/format.js';

const PROVIDER_META = {
  GOOGLE: { label: 'Google', bg: '#ffffff', border: true },
  TELEGRAM: { label: 'Telegram', bg: 'linear-gradient(135deg,#2AABEE,#229ED9)' },
  FACEBOOK: { label: 'Facebook', bg: '#1877F2' },
  X: { label: 'X', bg: '#000000' },
  PASSWORD: { label: 'Login / parol', bg: '#94a3b8' },
};

/** Tiny round badge marking how an account's owner signed up. */
function ProviderIcon({ provider }) {
  const key = PROVIDER_META[provider] ? provider : 'PASSWORD';
  const meta = PROVIDER_META[key];
  return (
    <span
      title={`${meta.label} orqali ro'yxatdan o'tgan`}
      aria-label={meta.label}
      style={{
        width: 18, height: 18, borderRadius: '50%', background: meta.bg,
        border: meta.border ? '1px solid #dadce0' : 'none', flex: '0 0 auto',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        verticalAlign: 'middle', marginRight: 6,
      }}
    >
      {key === 'GOOGLE' && (
        <svg viewBox="0 0 48 48" width="11" height="11" aria-hidden="true">
          <path fill="#FFC107" d="M43.611 20.083H42V20H24v8h11.303c-1.649 4.657-6.08 8-11.303 8-6.627 0-12-5.373-12-12s5.373-12 12-12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 12.955 4 4 12.955 4 24s8.955 20 20 20 20-8.955 20-20c0-1.341-.138-2.65-.389-3.917z"/>
          <path fill="#FF3D00" d="M6.306 14.691l6.571 4.819C14.655 15.108 18.961 12 24 12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 16.318 4 9.656 8.337 6.306 14.691z"/>
          <path fill="#4CAF50" d="M24 44c5.166 0 9.86-1.977 13.409-5.192l-6.19-5.238C29.211 35.091 26.715 36 24 36c-5.202 0-9.619-3.317-11.283-7.946l-6.522 5.025C9.505 39.556 16.227 44 24 44z"/>
          <path fill="#1976D2" d="M43.611 20.083H42V20H24v8h11.303c-.792 2.237-2.231 4.166-4.087 5.571l6.19 5.238C36.971 39.205 44 34 44 24c0-1.341-.138-2.65-.389-3.917z"/>
        </svg>
      )}
      {key === 'TELEGRAM' && (
        <svg viewBox="0 0 24 24" width="12" height="12" fill="#fff" aria-hidden="true">
          <path d="M9.78 18.65l.28-4.23 7.68-6.92c.34-.31-.07-.46-.52-.19L7.74 13.3 3.64 12c-.88-.25-.89-.86.2-1.3l15.97-6.16c.73-.33 1.43.18 1.15 1.3l-2.72 12.81c-.19.91-.74 1.13-1.5.71L12.6 16.3l-2.1 2.04c-.23.23-.42.42-.72.31z"/>
        </svg>
      )}
      {key === 'FACEBOOK' && (
        <svg viewBox="0 0 24 24" width="12" height="12" fill="#fff" aria-hidden="true">
          <path d="M15.12 5.32H17V2.14A26.11 26.11 0 0 0 14.26 2c-2.72 0-4.58 1.66-4.58 4.7v2.6H6.61v3.56h3.07V22h3.68v-9.14h3.06l.46-3.56h-3.52V7.05c0-1.03.28-1.73 1.76-1.73z"/>
        </svg>
      )}
      {key === 'X' && (
        <svg viewBox="0 0 24 24" width="10" height="10" fill="#fff" aria-hidden="true">
          <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24h-6.656l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/>
        </svg>
      )}
      {key === 'PASSWORD' && (
        <svg viewBox="0 0 24 24" width="11" height="11" fill="#fff" aria-hidden="true">
          <path d="M12 1a5 5 0 0 0-5 5v3H6a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2h-1V6a5 5 0 0 0-5-5zm3 8H9V6a3 3 0 0 1 6 0z"/>
        </svg>
      )}
    </span>
  );
}

/**
 * Super-admin only: list every paying account, create new ones, set
 * subscription expiry, block / unblock and reset user passwords. Other
 * roles never see this route (App.jsx gates it).
 */
export function Admin() {
  const t = useT();
  const toast = useToast();
  const { data, loading, error, reload } = useApi(() => AdminApi.listAccounts(), []);
  const [modal, setModal] = useState(null);

  const accounts = data || [];
  const totalAccounts = accounts.length;
  const blockedCount = accounts.filter((a) => a.blocked || a.expired).length;
  const activeCount = totalAccounts - blockedCount;
  // "New" = signed up in the last 3 days (the trial window) — surfaced so the
  // super-admin sees fresh signups at a glance in the panel.
  const isNew = (a) => a.createdAt
    && (Date.now() - new Date(a.createdAt).getTime()) < 3 * 86400000;
  const newSignups = accounts.filter(isNew).length;

  const setBlocked = async (acc) => {
    try {
      await AdminApi.setBlocked(acc.id, !acc.blocked);
      toast.success(acc.blocked ? t('Akkaunt ochildi') : t('Akkaunt bloklandi'));
      reload();
    } catch (err) {
      toast.error(err.message);
    }
  };

  const confirmDelete = async () => {
    try {
      await AdminApi.deleteAccount(modal.item.id);
      toast.success(t("Akkaunt o'chirildi"));
      setModal(null);
      reload();
    } catch (err) {
      toast.error(err.message);
    }
  };

  return (
    <>
      <PageHeader
        title={t('Super-admin panel')}
        desc={t('Mijoz akkauntlari, obunalar, parollar boshqaruvi')}
      >
        <Link to="/admin/audit" className="btn btn-ghost">
          📜 {t('Audit log')}
        </Link>
        <button className="btn btn-primary" onClick={() => setModal({ type: 'create' })}>
          + {t('Yangi akkaunt')}
        </button>
      </PageHeader>

      <div className="admin-stats section">
        <div className="admin-stat tone-emerald">
          <span className="ds-eyebrow recv">{t('Jami akkauntlar')}</span>
          <div className="admin-stat-value">{totalAccounts}</div>
        </div>
        <div className="admin-stat tone-blue">
          <span className="ds-eyebrow" style={{ color: '#10b981',
            background: 'rgba(16, 185, 129, .10)',
            border: '1px solid rgba(16, 185, 129, .28)' }}>
            🆕 {t('Yangi (3 kun)')}
          </span>
          <div className="admin-stat-value">{newSignups}</div>
        </div>
        <div className="admin-stat tone-blue">
          <span className="ds-eyebrow" style={{ color: '#3b82f6',
            background: 'rgba(59, 130, 246, .10)',
            border: '1px solid rgba(59, 130, 246, .28)' }}>
            {t('Faol')}
          </span>
          <div className="admin-stat-value">{activeCount}</div>
        </div>
        <div className="admin-stat tone-red">
          <span className="ds-eyebrow liab">{t('Bloklangan / muddati tugagan')}</span>
          <div className="admin-stat-value">{blockedCount}</div>
        </div>
      </div>

      <div className="card">
        <div className="card-head">
          <h2>{t("Akkauntlar ro'yxati")}</h2>
          <span className="hint">{totalAccounts} {t('ta')}</span>
        </div>
        <Loader loading={loading} error={error} onRetry={reload}>
          {accounts.length === 0 ? (
            <EmptyState icon="👥" text={t("Hozircha akkaunt yo'q")} />
          ) : (
            <div className="table-wrap">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>{t('Akkaunt')}</th>
                    <th>{t('Telefon')}</th>
                    <th>{t('Obuna tugashi')}</th>
                    <th>{t("Foydalanuvchilar")}</th>
                    <th>{t('Holat')}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {accounts.map((a) => (
                    <tr key={a.id}>
                      <td className="name-cell">
                        <ProviderIcon provider={a.signupProvider} />
                        <Link
                          to={`/admin/accounts/${a.id}`}
                          className="admin-account-link"
                          title={t('Akkaunt sozlamalari')}
                        >
                          <strong>{a.name}</strong>
                        </Link>
                        {isNew(a) && (
                          <span className="badge badge-naqd" style={{ marginLeft: 6 }}>
                            🆕 {t('Yangi')}
                          </span>
                        )}
                        {a.contactNote && (
                          <div className="faint" style={{ fontSize: 11 }}>
                            {a.contactNote}
                          </div>
                        )}
                      </td>
                      <td className="faint mono">{a.contactPhone || '—'}</td>
                      <td className="faint mono">
                        {a.subscriptionExpires ? (
                          <>
                            {formatDate(a.subscriptionExpires)}
                            {!a.expired && a.daysUntilBlock <= 4 && (
                              <span className="badge badge-aralash"
                                    style={{ marginLeft: 6 }}>
                                {a.daysUntilBlock} {t('kun')}
                              </span>
                            )}
                          </>
                        ) : '∞'}
                      </td>
                      <td className="num mono">{a.userCount}</td>
                      <td>
                        {a.blocked ? (
                          <span className="badge badge-qarzga">
                            🚫 {t('Bloklangan')}
                          </span>
                        ) : a.expired ? (
                          <span className="badge badge-aralash">
                            ⏰ {t('Muddati tugagan')}
                          </span>
                        ) : (
                          <span className="badge badge-naqd">
                            ✓ {t('Faol')}
                          </span>
                        )}
                      </td>
                      <td>
                        <div className="row-actions">
                          <button className="icon-btn"
                                  title={t('Tahrirlash')}
                                  onClick={() => setModal({ type: 'edit', item: a })}>
                            ✏️
                          </button>
                          <button className="icon-btn"
                                  title={t('Foydalanuvchilar')}
                                  onClick={() => setModal({ type: 'users', item: a })}>
                            👥
                          </button>
                          <button className="icon-btn"
                                  title={a.blocked ? t('Akkauntni ochish') : t('Akkauntni bloklash')}
                                  onClick={() => setBlocked(a)}
                                  disabled={a.id === 1}>
                            {a.blocked ? '🔓' : '🔒'}
                          </button>
                          <button className="icon-btn danger"
                                  title={t("O'chirish")}
                                  onClick={() => setModal({ type: 'delete', item: a })}
                                  disabled={a.id === 1}>
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

      {modal?.type === 'create' && (
        <CreateAccountModal
          onSubmit={async (body) => {
            await AdminApi.createAccount(body);
            toast.success(t('Akkaunt yaratildi'));
            reload();
          }}
          onClose={() => setModal(null)}
        />
      )}

      {modal?.type === 'edit' && (
        <EditAccountModal
          account={modal.item}
          onSubmit={async (body) => {
            await AdminApi.updateAccount(modal.item.id, body);
            toast.success(t('Akkaunt yangilandi'));
            reload();
          }}
          onClose={() => setModal(null)}
        />
      )}

      {modal?.type === 'users' && (
        <UsersModal
          account={modal.item}
          onClose={() => { setModal(null); reload(); }}
        />
      )}

      {modal?.type === 'delete' && (
        <ConfirmDialog
          title={t("Akkauntni o'chirish")}
          message={t("Bu akkauntni va barcha foydalanuvchilarini o'chirmoqchimisiz?")}
          confirmLabel={t("O'chirish")}
          onConfirm={confirmDelete}
          onCancel={() => setModal(null)}
        />
      )}
    </>
  );
}

/* ----------------------------------------------------------- modals */

function CreateAccountModal({ onSubmit, onClose }) {
  const t = useT();
  const [form, setForm] = useState({
    name: '', contactPhone: '', contactNote: '',
    subscriptionDays: 30,
    ownerUsername: '', ownerPassword: '', ownerFullName: '',
  });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const update = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async () => {
    if (!form.name.trim() || !form.ownerUsername.trim() || !form.ownerPassword) {
      setError(t('Akkaunt nomi, login va parolni kiriting'));
      return;
    }
    const days = Number(form.subscriptionDays);
    const expires = days > 0
      ? new Date(Date.now() + days * 86400000).toISOString().slice(0, 10)
      : null;
    setBusy(true);
    try {
      await onSubmit({
        name: form.name.trim(),
        contactPhone: form.contactPhone.trim() || null,
        contactNote: form.contactNote.trim() || null,
        subscriptionExpires: expires,
        ownerUsername: form.ownerUsername.trim(),
        ownerPassword: form.ownerPassword,
        ownerFullName: form.ownerFullName.trim() || null,
      });
      onClose();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <Modal
      title={t('Yangi akkaunt yaratish')}
      onClose={onClose}
      wide
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={busy}>
            {t('Bekor qilish')}
          </button>
          <button className="btn btn-primary" onClick={submit} disabled={busy}>
            {busy ? t('Saqlanmoqda...') : t('Yaratish')}
          </button>
        </>
      }
    >
      <p className="muted" style={{ marginBottom: 12, fontSize: 12 }}>
        {t("Yangi mijoz akkaunti yaratiladi va birinchi foydalanuvchi (owner) qo'shiladi.")}
      </p>

      <div className="section-label">🏢 {t('Akkaunt')}</div>
      <div className="form-row">
        <div className="field">
          <label>{t('Akkaunt nomi *')}</label>
          <input className="input" value={form.name}
                 onChange={update('name')}
                 placeholder="Barakat Market — Chilonzor" />
        </div>
        <div className="field">
          <label>{t('Telefon')}</label>
          <PhoneInput value={form.contactPhone}
                      onChange={(v) => setForm({ ...form, contactPhone: v })} />
        </div>
      </div>
      <div className="field">
        <label>{t('Izoh (ixtiyoriy)')}</label>
        <input className="input" value={form.contactNote}
               onChange={update('contactNote')} />
      </div>
      <div className="field">
        <label>{t('Obuna muddati')}</label>
        <select className="select" value={form.subscriptionDays}
                onChange={update('subscriptionDays')}>
          <option value={30}>{t('30 kun (1 oy)')}</option>
          <option value={60}>{t('60 kun (2 oy)')}</option>
          <option value={90}>{t('90 kun (3 oy)')}</option>
          <option value={180}>{t('180 kun (6 oy)')}</option>
          <option value={365}>{t('365 kun (1 yil)')}</option>
          <option value={0}>{t('Cheksiz (test)')}</option>
        </select>
      </div>

      <div className="section-label" style={{ marginTop: 18 }}>
        👤 {t('Owner (birinchi foydalanuvchi)')}
      </div>
      <div className="form-row">
        <div className="field">
          <label>{t('Login *')}</label>
          <input className="input" value={form.ownerUsername}
                 onChange={update('ownerUsername')}
                 placeholder="sarvar" autoComplete="off" />
        </div>
        <div className="field">
          <label>{t('Parol *')}</label>
          <input className="input" type="text" value={form.ownerPassword}
                 onChange={update('ownerPassword')}
                 placeholder="kamida 4 ta belgi" autoComplete="off" />
        </div>
      </div>
      <div className="field">
        <label>{t('Ism-familiya (ixtiyoriy)')}</label>
        <input className="input" value={form.ownerFullName}
               onChange={update('ownerFullName')} />
      </div>
      {error && <div style={{ color: 'var(--red)', fontSize: 12 }}>{error}</div>}
    </Modal>
  );
}

function EditAccountModal({ account, onSubmit, onClose }) {
  const t = useT();
  const [form, setForm] = useState({
    name: account.name,
    contactPhone: account.contactPhone || '',
    contactNote: account.contactNote || '',
    subscriptionExpires: account.subscriptionExpires || '',
  });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const update = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const extend = (days) => {
    const base = form.subscriptionExpires
      ? new Date(`${form.subscriptionExpires}T00:00:00`)
      : new Date();
    const next = new Date(base.getTime() + days * 86400000);
    setForm({ ...form, subscriptionExpires: next.toISOString().slice(0, 10) });
  };

  const submit = async () => {
    if (!form.name.trim()) {
      setError(t('Akkaunt nomi kiritilishi shart'));
      return;
    }
    setBusy(true);
    try {
      await onSubmit({
        name: form.name.trim(),
        contactPhone: form.contactPhone.trim() || null,
        contactNote: form.contactNote.trim() || null,
        subscriptionExpires: form.subscriptionExpires || null,
      });
      onClose();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <Modal
      title={t('Akkauntni tahrirlash')}
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
        <label>{t('Akkaunt nomi')}</label>
        <input className="input" value={form.name} onChange={update('name')} />
      </div>
      <div className="form-row">
        <div className="field">
          <label>{t('Telefon')}</label>
          <PhoneInput value={form.contactPhone}
                      onChange={(v) => setForm({ ...form, contactPhone: v })} />
        </div>
        <div className="field">
          <label>{t('Obuna tugashi')}</label>
          <input className="input" type="date"
                 value={form.subscriptionExpires}
                 onChange={update('subscriptionExpires')} />
        </div>
      </div>
      <div className="chip-row" style={{ marginBottom: 12 }}>
        <button type="button" className="chip" onClick={() => extend(30)}>+ 30 kun</button>
        <button type="button" className="chip" onClick={() => extend(60)}>+ 60 kun</button>
        <button type="button" className="chip" onClick={() => extend(90)}>+ 90 kun</button>
        <button type="button" className="chip" onClick={() => extend(365)}>+ 1 yil</button>
        <button type="button" className="chip"
                onClick={() => setForm({ ...form, subscriptionExpires: '' })}>
          ∞ {t('Cheksiz')}
        </button>
      </div>
      <div className="field">
        <label>{t('Izoh')}</label>
        <input className="input" value={form.contactNote}
               onChange={update('contactNote')} />
      </div>
      {error && <div style={{ color: 'var(--red)', fontSize: 12 }}>{error}</div>}
    </Modal>
  );
}

function UsersModal({ account, onClose }) {
  const t = useT();
  const toast = useToast();
  const { data, loading, error, reload } =
    useApi(() => AdminApi.accountDetail(account.id), [account.id]);
  const [adding, setAdding] = useState(false);
  const [resetting, setResetting] = useState(null);
  const [confirmRemoveUser, setConfirmRemoveUser] = useState(null);

  const detail = data || { users: [] };

  const removeUser = async (u) => {
    try {
      await AdminApi.deleteUser(u.id);
      toast.success(t("Foydalanuvchi o'chirildi"));
      reload();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setConfirmRemoveUser(null);
    }
  };

  return (
    <Modal
      title={`${t('Foydalanuvchilar')} — ${account.name}`}
      onClose={onClose}
      wide
      footer={
        <button className="btn btn-primary" onClick={() => setAdding(true)}>
          + {t('Yangi foydalanuvchi')}
        </button>
      }
    >
      <Loader loading={loading} error={error} onRetry={reload}>
        {detail.users.length === 0 ? (
          <EmptyState icon="👤" text={t("Foydalanuvchi yo'q")} />
        ) : (
          <table className="tbl">
            <thead>
              <tr>
                <th>{t('Login')}</th>
                <th>{t('Ism')}</th>
                <th>{t('Role')}</th>
                <th>{t('Oxirgi kirish')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {detail.users.map((u) => (
                <tr key={u.id}>
                  <td><b>{u.username}</b></td>
                  <td className="faint">{u.fullName || '—'}</td>
                  <td>
                    <span className="badge badge-muted" style={{ fontSize: 10 }}>
                      {u.role}
                    </span>
                  </td>
                  <td className="faint mono" style={{ fontSize: 11 }}>
                    {u.lastLoginAt ? formatDate(u.lastLoginAt.slice(0, 10)) : '—'}
                  </td>
                  <td>
                    <div className="row-actions">
                      <button className="icon-btn"
                              title={t("Parolni o'zgartirish")}
                              onClick={() => setResetting(u)}>
                        🔑
                      </button>
                      <button className="icon-btn danger"
                              title={t("O'chirish")}
                              disabled={u.role === 'SUPER_ADMIN'}
                              onClick={() => setConfirmRemoveUser(u)}>
                        🗑
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Loader>

      {adding && (
        <AddUserModal
          accountId={account.id}
          onClose={() => setAdding(false)}
          onCreated={() => { setAdding(false); reload(); }}
        />
      )}

      {resetting && (
        <ResetPasswordModal
          user={resetting}
          onClose={() => setResetting(null)}
          onDone={() => { setResetting(null); reload(); }}
        />
      )}

      {confirmRemoveUser && (
        <ConfirmDialog
          title={t("Foydalanuvchini o'chirish")}
          message={`"${confirmRemoveUser.username}" ${t("foydalanuvchisini o'chirishni tasdiqlaysizmi")}?`}
          confirmLabel={t("O'chirish")}
          onConfirm={() => removeUser(confirmRemoveUser)}
          onCancel={() => setConfirmRemoveUser(null)}
        />
      )}
    </Modal>
  );
}

function AddUserModal({ accountId, onClose, onCreated }) {
  const t = useT();
  const toast = useToast();
  const [form, setForm] = useState({
    username: '', password: '', fullName: '', role: 'SHOP_USER',
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const update = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async () => {
    if (!form.username.trim() || !form.password) {
      setError(t('Login va parolni kiriting'));
      return;
    }
    setBusy(true);
    try {
      await AdminApi.createUser(accountId, {
        username: form.username.trim(),
        password: form.password,
        fullName: form.fullName.trim() || null,
        role: form.role,
      });
      toast.success(t("Foydalanuvchi qo'shildi"));
      onCreated();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <Modal
      title={t('Yangi foydalanuvchi')}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={busy}>
            {t('Bekor qilish')}
          </button>
          <button className="btn btn-primary" onClick={submit} disabled={busy}>
            {busy ? t('Saqlanmoqda...') : t('Yaratish')}
          </button>
        </>
      }
    >
      <div className="form-row">
        <div className="field">
          <label>{t('Login *')}</label>
          <input className="input" value={form.username}
                 onChange={update('username')} autoComplete="off" />
        </div>
        <div className="field">
          <label>{t('Parol *')}</label>
          <input className="input" type="text" value={form.password}
                 onChange={update('password')} autoComplete="off" />
        </div>
      </div>
      <div className="field">
        <label>{t('Ism-familiya')}</label>
        <input className="input" value={form.fullName}
               onChange={update('fullName')} />
      </div>
      <div className="field">
        <label>{t('Role')}</label>
        <select className="select" value={form.role} onChange={update('role')}>
          <option value="ACCOUNT_OWNER">ACCOUNT_OWNER</option>
          <option value="SHOP_USER">SHOP_USER</option>
        </select>
      </div>
      {error && <div style={{ color: 'var(--red)', fontSize: 12 }}>{error}</div>}
    </Modal>
  );
}

function ResetPasswordModal({ user, onClose, onDone }) {
  const t = useT();
  const toast = useToast();
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    if (!password || password.length < 4) {
      toast.error(t("Kamida 4 ta belgi kiriting"));
      return;
    }
    setBusy(true);
    try {
      await AdminApi.resetPassword(user.id, password);
      toast.success(t('Parol yangilandi'));
      onDone();
    } catch (err) {
      toast.error(err.message);
      setBusy(false);
    }
  };

  return (
    <Modal
      title={`${t("Parolni o'zgartirish")} — ${user.username}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={busy}>
            {t('Bekor qilish')}
          </button>
          <button className="btn btn-primary" onClick={submit} disabled={busy}>
            {busy ? t('Saqlanmoqda...') : t("O'zgartirish")}
          </button>
        </>
      }
    >
      <div className="field">
        <label>{t('Yangi parol')}</label>
        <input className="input" type="text" value={password} autoFocus
               onChange={(e) => setPassword(e.target.value)}
               placeholder="kamida 4 ta belgi" autoComplete="off" />
      </div>
    </Modal>
  );
}
