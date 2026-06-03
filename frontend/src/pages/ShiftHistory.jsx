import { useState } from 'react';
import { ShiftApi } from '../api/endpoints.js';
import { useT } from '../context/Settings.jsx';
import { ConfirmDialog } from '../components/Modal.jsx';
import { useToast } from '../components/Toast.jsx';
import { EmptyState, Loader, PageHeader } from '../components/ui.jsx';
import { useApi } from '../hooks/useApi.js';
import { formatDateTime, formatDuration, usd } from '../lib/format.js';

export function ShiftHistory() {
  const { data, loading, error, reload } = useApi(() => ShiftApi.history(), []);
  const [confirming, setConfirming] = useState(false);
  const toast = useToast();
  const t = useT();

  const clear = async () => {
    try {
      const result = await ShiftApi.clearHistory();
      toast.success(`${result.removed} ${t("ta yopilgan smena o'chirildi")}`);
      setConfirming(false);
      reload();
    } catch (err) {
      toast.error(err.message);
    }
  };

  const shifts = data || [];

  return (
    <>
      <PageHeader title={t('Smena tarixi')} desc={t('Barcha ochilgan va yopilgan smenalar')}>
        <button
          className="btn btn-ghost"
          onClick={() => setConfirming(true)}
          disabled={shifts.length === 0}
        >
          🧹 {t('Tarixni tozalash')}
        </button>
      </PageHeader>

      <div className="card">
        <div className="card-head">
          <h2>{t('Smenalar')}</h2>
          <span className="hint">{shifts.length} {t('ta')}</span>
        </div>
        <Loader loading={loading} error={error} onRetry={reload}>
          {shifts.length === 0 ? (
            <EmptyState icon="🕘" text={t('Hali smena ochilmagan')} />
          ) : (
            <div className="table-wrap">
              <table className="tbl">
                <thead>
                  <tr>
                    <th>{t('Ochildi')}</th>
                    <th>{t('Yopildi')}</th>
                    <th>{t('Davomiyligi')}</th>
                    <th>{t('Kim ochdi')}</th>
                    <th>{t('Kutilgan')}</th>
                    <th>{t('Sanaldi')}</th>
                    <th>{t('Farq')}</th>
                    <th>{t('Holat')}</th>
                  </tr>
                </thead>
                <tbody>
                  {shifts.map((s) => (
                    <tr key={s.id}>
                      <td className="nowrap">{formatDateTime(s.openedAt)}</td>
                      <td className="nowrap">
                        {s.closedAt ? formatDateTime(s.closedAt) : <span className="faint">—</span>}
                      </td>
                      <td>{formatDuration(s.durationMinutes)}</td>
                      <td>{s.openedBy || <span className="faint">—</span>}</td>
                      <td className="nowrap mono">
                        {s.expectedCash == null ? <span className="faint">—</span> : usd(s.expectedCash)}
                      </td>
                      <td className="nowrap mono">
                        {s.countedCash == null ? <span className="faint">—</span> : usd(s.countedCash)}
                      </td>
                      <td className="nowrap">
                        {s.cashDifference == null ? (
                          <span className="faint">—</span>
                        ) : (
                          <span
                            className="mono"
                            style={{
                              fontWeight: 700,
                              color:
                                s.cashDifference < 0
                                  ? 'var(--red)'
                                  : s.cashDifference > 0
                                    ? 'var(--green)'
                                    : undefined,
                            }}
                          >
                            {usd(s.cashDifference)}
                          </span>
                        )}
                      </td>
                      <td>
                        {s.status === 'OPEN' ? (
                          <span className="badge badge-naqd">{t('Ochiq')}</span>
                        ) : (
                          <span className="badge badge-muted">{t('Yopilgan')}</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Loader>
      </div>

      {confirming && (
        <ConfirmDialog
          title={t('Tarixni tozalash')}
          message={t("Barcha yopilgan smenalar o'chiriladi. Ochiq smena saqlanadi. Davom etamizmi?")}
          confirmLabel={t('Tozalash')}
          onConfirm={clear}
          onCancel={() => setConfirming(false)}
        />
      )}
    </>
  );
}
