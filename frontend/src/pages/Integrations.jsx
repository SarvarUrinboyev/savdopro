import { useState } from 'react';
import { IntegrationsApi } from '../api/endpoints.js';
import { Modal } from '../components/Modal.jsx';
import { useToast } from '../components/Toast.jsx';
import { EmptyState, Loader } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { formatDate } from '../lib/format.js';

/**
 * Owner page to manage external integrations: API keys for the Open API
 * (/api/v1) and outbound webhook subscriptions. Secrets (the API-key plaintext
 * and the webhook signing secret) are shown exactly once at creation.
 */
export function Integrations() {
  const t = useT();
  const toast = useToast();
  const { data, loading, error, reload } = useApi(
    () => Promise.all([
      IntegrationsApi.meta(),
      IntegrationsApi.listKeys(),
      IntegrationsApi.listWebhooks(),
      IntegrationsApi.deliveries(),
    ]),
    [],
  );
  const meta = data ? data[0] : { scopes: [], eventTypes: [] };
  const keys = data ? data[1] : [];
  const webhooks = data ? data[2] : [];
  const deliveries = data ? data[3] : [];

  const [keyModal, setKeyModal] = useState(false);
  const [hookModal, setHookModal] = useState(false);
  const [secret, setSecret] = useState(null); // { title, value, hint }

  return (
    <>
      <div className="section flex-between" style={{ flexWrap: 'wrap', gap: 8, marginBottom: 4 }}>
        <span style={{ fontWeight: 600, color: 'var(--text-faint)', fontSize: 13 }}>
          🔌 {t('Tashqi integratsiyalar — API kalitlar va webhooklar')}
        </span>
        <a className="btn btn-ghost btn-sm" href="/swagger-ui.html?urls.primaryName=public"
           target="_blank" rel="noreferrer">
          📖 {t('API hujjati')} →
        </a>
      </div>

      {secret && (
        <div className="card card-pad section" style={{ borderLeft: '4px solid var(--green, #10b981)' }}>
          <div style={{ fontWeight: 600, marginBottom: 6 }}>{secret.title}</div>
          <div className="mono" style={{ wordBreak: 'break-all', background: 'rgba(0,0,0,.05)',
            padding: '8px 10px', borderRadius: 6, fontSize: 13 }}>{secret.value}</div>
          <div className="flex-between" style={{ marginTop: 8 }}>
            <span className="faint" style={{ fontSize: 12 }}>{secret.hint}</span>
            <div style={{ display: 'flex', gap: 8 }}>
              <button className="btn btn-ghost btn-sm" onClick={() => copy(secret.value, t, toast)}>
                📋 {t('Nusxa olish')}
              </button>
              <button className="btn btn-ghost btn-sm" onClick={() => setSecret(null)}>
                {t('Yopdim')}
              </button>
            </div>
          </div>
        </div>
      )}

      <Loader loading={loading} error={error} onRetry={reload}>
        {data && (
          <>
            <ApiKeysCard
              keys={keys}
              onNew={() => setKeyModal(true)}
              onReload={reload}
            />
            <WebhooksCard
              webhooks={webhooks}
              onNew={() => setHookModal(true)}
              onReload={reload}
            />
            <DeliveriesCard deliveries={deliveries} />
          </>
        )}
      </Loader>

      {keyModal && (
        <KeyModal
          scopes={meta.scopes || []}
          onClose={() => setKeyModal(false)}
          onCreated={(issued) => {
            setKeyModal(false);
            setSecret({
              title: t('Yangi API kalit yaratildi'),
              value: issued.secret,
              hint: t('Bu kalit faqat hozir ko\'rsatiladi — saqlab oling.'),
            });
            reload();
          }}
        />
      )}
      {hookModal && (
        <WebhookModal
          eventTypes={meta.eventTypes || []}
          onClose={() => setHookModal(false)}
          onCreated={(created) => {
            setHookModal(false);
            setSecret({
              title: t('Webhook yaratildi — imzo kaliti (signing secret)'),
              value: created.secret,
              hint: t('Bu maxfiy kalit faqat hozir ko\'rsatiladi — saqlab oling.'),
            });
            reload();
          }}
        />
      )}
    </>
  );
}

function copy(text, t, toast) {
  try {
    navigator.clipboard.writeText(text);
    if (toast) toast.success(t('Nusxa olindi'));
  } catch { /* clipboard unavailable */ }
}

function ApiKeysCard({ keys, onNew, onReload }) {
  const t = useT();
  const toast = useToast();
  const revoke = async (id) => {
    if (!window.confirm(t('Kalitni bekor qilasizmi? Bu integratsiya ishlamay qoladi.'))) return;
    try {
      await IntegrationsApi.revokeKey(id);
      toast.success(t('Kalit bekor qilindi'));
      onReload();
    } catch (e) { toast.error(e.message); }
  };
  return (
    <div className="card section">
      <div className="card-head">
        <h2>{t('API kalitlar')}</h2>
        <button className="btn btn-primary btn-sm" onClick={onNew}>+ {t('Yangi kalit')}</button>
      </div>
      <div className="card-pad">
        {keys.length === 0 ? (
          <EmptyState icon="🔑" text={t('Hali API kalit yo\'q')} />
        ) : (
          <div className="list-stack">
            {keys.map((k) => (
              <div key={k.id} className="flex-between" style={{ gap: 8, opacity: k.active ? 1 : 0.5 }}>
                <div>
                  <div style={{ fontWeight: 600 }}>{k.name}{!k.active && ' — ' + t('bekor qilingan')}</div>
                  <div className="faint" style={{ fontSize: 12 }}>
                    <span className="mono">{k.prefix}…</span> · {(k.scopes || []).join(', ')}
                    {k.lastUsedAt ? ' · ' + t('oxirgi') + ' ' + formatDate(k.lastUsedAt) : ''}
                  </div>
                </div>
                {k.active && (
                  <button className="btn btn-ghost btn-sm" onClick={() => revoke(k.id)}>
                    {t('Bekor qilish')}
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function WebhooksCard({ webhooks, onNew, onReload }) {
  const t = useT();
  const toast = useToast();
  const del = async (id) => {
    if (!window.confirm(t('Webhookni o\'chirasizmi?'))) return;
    try { await IntegrationsApi.deleteWebhook(id); toast.success(t('O\'chirildi')); onReload(); }
    catch (e) { toast.error(e.message); }
  };
  const test = async (id) => {
    try { await IntegrationsApi.testWebhook(id); toast.success(t('Test yuborildi — yetkazib berishni kuting')); }
    catch (e) { toast.error(e.message); }
  };
  return (
    <div className="card section">
      <div className="card-head">
        <h2>{t('Webhooklar')}</h2>
        <button className="btn btn-primary btn-sm" onClick={onNew}>+ {t('Yangi webhook')}</button>
      </div>
      <div className="card-pad">
        {webhooks.length === 0 ? (
          <EmptyState icon="📡" text={t('Hali webhook yo\'q')} />
        ) : (
          <div className="list-stack">
            {webhooks.map((w) => (
              <div key={w.id} className="flex-between" style={{ gap: 8 }}>
                <div style={{ minWidth: 0 }}>
                  <div className="mono" style={{ fontSize: 13, wordBreak: 'break-all' }}>{w.url}</div>
                  <div className="faint" style={{ fontSize: 12 }}>{(w.events || []).join(', ')}</div>
                </div>
                <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
                  <button className="btn btn-ghost btn-sm" onClick={() => test(w.id)}>{t('Test')}</button>
                  <button className="btn btn-ghost btn-sm" onClick={() => del(w.id)}>{t('O\'chirish')}</button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function DeliveriesCard({ deliveries }) {
  const t = useT();
  if (!deliveries || deliveries.length === 0) return null;
  return (
    <div className="card section">
      <div className="card-head">
        <h2>{t('Oxirgi yetkazib berishlar')}</h2>
      </div>
      <div className="card-pad">
        <div className="list-stack">
          {deliveries.slice(0, 30).map((d) => (
            <div key={d.id} className="flex-between" style={{ fontSize: 13 }}>
              <span>{statusIcon(d.status)} {d.eventType}</span>
              <span className="faint">
                {d.attempts > 0 ? t('urinish') + ' ' + d.attempts + ' · ' : ''}
                {formatDate(d.createdAt)}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function statusIcon(status) {
  if (status === 'DELIVERED') return '✅';
  if (status === 'FAILED') return '❌';
  return '⏳';
}

function KeyModal({ scopes, onClose, onCreated }) {
  const t = useT();
  const toast = useToast();
  const [name, setName] = useState('');
  const [picked, setPicked] = useState([]);
  const [busy, setBusy] = useState(false);
  const toggle = (s) => setPicked((p) => p.includes(s) ? p.filter((x) => x !== s) : [...p, s]);
  const save = async () => {
    if (!name.trim()) { toast.error(t('Nom kiriting')); return; }
    if (picked.length === 0) { toast.error(t('Kamida bitta ruxsat tanlang')); return; }
    setBusy(true);
    try {
      const issued = await IntegrationsApi.createKey({ name: name.trim(), scopes: picked });
      onCreated(issued);
    } catch (e) { toast.error(e.message); setBusy(false); }
  };
  return (
    <Modal title={t('Yangi API kalit')} onClose={onClose} footer={
      <>
        <button className="btn btn-ghost" onClick={onClose} disabled={busy}>{t('Bekor qilish')}</button>
        <button className="btn btn-primary" onClick={save} disabled={busy}>{busy ? '…' : t('Yaratish')}</button>
      </>
    }>
      <div className="field">
        <label>{t('Nom (integratsiya)')}</label>
        <input className="input" autoFocus value={name} onChange={(e) => setName(e.target.value)}
               placeholder="Uzum marketplace" />
      </div>
      <div className="field">
        <label>{t('Ruxsatlar (scopes)')}</label>
        <CheckboxList options={scopes} picked={picked} onToggle={toggle} />
      </div>
    </Modal>
  );
}

function WebhookModal({ eventTypes, onClose, onCreated }) {
  const t = useT();
  const toast = useToast();
  const [url, setUrl] = useState('');
  const [picked, setPicked] = useState([]);
  const [busy, setBusy] = useState(false);
  const toggle = (s) => setPicked((p) => p.includes(s) ? p.filter((x) => x !== s) : [...p, s]);
  const save = async () => {
    if (!url.trim()) { toast.error(t('URL kiriting')); return; }
    if (picked.length === 0) { toast.error(t('Kamida bitta hodisa tanlang')); return; }
    setBusy(true);
    try {
      const created = await IntegrationsApi.createWebhook({ url: url.trim(), events: picked });
      onCreated(created);
    } catch (e) { toast.error(e.message); setBusy(false); }
  };
  return (
    <Modal title={t('Yangi webhook')} onClose={onClose} footer={
      <>
        <button className="btn btn-ghost" onClick={onClose} disabled={busy}>{t('Bekor qilish')}</button>
        <button className="btn btn-primary" onClick={save} disabled={busy}>{busy ? '…' : t('Yaratish')}</button>
      </>
    }>
      <div className="field">
        <label>{t('URL (https)')}</label>
        <input className="input" autoFocus value={url} onChange={(e) => setUrl(e.target.value)}
               placeholder="https://example.com/webhooks/savdopro" />
        <div className="field-hint">{t('Faqat https. Ichki/lokal manzillar qabul qilinmaydi.')}</div>
      </div>
      <div className="field">
        <label>{t('Hodisalar (events)')}</label>
        <CheckboxList options={eventTypes} picked={picked} onToggle={toggle} />
      </div>
    </Modal>
  );
}

function CheckboxList({ options, picked, onToggle }) {
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
      {options.map((o) => (
        <label key={o} className={`chip ${picked.includes(o) ? 'chip-on' : ''}`}
               style={{ cursor: 'pointer', userSelect: 'none' }}>
          <input type="checkbox" checked={picked.includes(o)} onChange={() => onToggle(o)}
                 style={{ marginRight: 6 }} />
          {o}
        </label>
      ))}
    </div>
  );
}
