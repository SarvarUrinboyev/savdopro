import { useState } from 'react';
import { AiApi } from '../api/endpoints.js';
import { useToast } from './Toast.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { formatDate } from '../lib/format.js';

/**
 * Persisted anomaly history with an inline acknowledge action. Renders nothing
 * when there's no history — a healthy shop shouldn't see this card. Acknowledged
 * rows stay (dimmed) for context; acknowledging removes them from the live
 * AnomalyBanner on the next reload.
 */
export function AnomalyHistory() {
  const t = useT();
  const toast = useToast();
  const { data, reload } = useApi(() => AiApi.anomalyHistory({ limit: 50 }), []);
  const [busy, setBusy] = useState(null);
  const rows = data || [];
  if (rows.length === 0) return null;

  const acknowledge = async (id) => {
    setBusy(id);
    try {
      await AiApi.acknowledgeAnomaly(id);
      toast.success(t('Belgilandi'));
      reload();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="card section">
      <div className="card-head">
        <h2>{t('Anomaliyalar tarixi')}</h2>
        <span className="hint">{t('AI nazorati aniqlagan shubhali holatlar')}</span>
      </div>
      <div className="card-pad">
        <div className="anomaly-list">
          {rows.map((a) => (
            <div
              key={a.id}
              className={`anomaly-row sev-${a.severity}${a.acknowledged ? ' ack' : ''}`}
            >
              <span className="anomaly-ico">{iconFor(a.severity)}</span>
              <div className="anomaly-text">
                <div className="anomaly-msg">{a.message}</div>
                <div className="anomaly-time">
                  {formatDate(a.occurredOn)}
                  {a.acknowledged && a.acknowledgedBy
                    ? ` · ${t('belgiladi')}: ${a.acknowledgedBy}`
                    : ''}
                </div>
              </div>
              {!a.acknowledged && (
                <button
                  className="btn btn-ghost btn-sm anomaly-ack-btn"
                  disabled={busy === a.id}
                  onClick={() => acknowledge(a.id)}
                >
                  {busy === a.id ? '…' : `✓ ${t('Tasdiqlash')}`}
                </button>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function iconFor(sev) {
  if (sev === 'critical') return '🚨';
  if (sev === 'warn') return '⚠️';
  return 'ℹ️';
}
