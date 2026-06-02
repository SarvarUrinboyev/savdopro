import { useState } from 'react';
import { AiApi, PosApi, ProductApi, ReportApi } from '../api/endpoints.js';
import { ExportButton } from '../components/ExportButton.jsx';
import { useToast } from '../components/Toast.jsx';
import { EmptyState, Loader, Spinner } from '../components/ui.jsx';
import { useT } from '../context/Settings.jsx';
import { useApi } from '../hooks/useApi.js';
import { shiftIso, todayIso, usd } from '../lib/format.js';
import { generatePriceTagsPdf } from '../lib/priceTagsPdf.js';

export function Reports() {
  const t = useT();

  return (
    <div className="grid" style={{ gap: 24 }}>
      <DailyReportSection t={t} />
      <CashierStatsSection t={t} />
      <ReorderQueueSection t={t} />
      <SlowMoversSection t={t} />
      <ProfitByProductSection t={t} />
      <HourlySalesSection t={t} />
      <PriceTagsSection t={t} />
      <PdfReportSection t={t} />
    </div>
  );
}

/* ──────────────────────────────────────────────────────────────
   Cashier performance (Kassirlar samaradorligi) — last 30 days
────────────────────────────────────────────────────────────── */
function CashierStatsSection({ t }) {
  const { data, loading, error, reload } = useApi(
    () => PosApi.cashierStats(shiftIso(-29), todayIso()), []);
  const rows = data || [];
  const th = { padding: '6px 8px', textAlign: 'right' };
  const td = { padding: '8px', textAlign: 'right' };
  return (
    <div className="card">
      <div className="card-head">
        <h2>👤 {t('Kassirlar samaradorligi')}</h2>
        <span className="hint">{t('oxirgi 30 kun')}</span>
      </div>
      <div className="card-pad">
        <Loader loading={loading} error={error} onRetry={reload}>
          {rows.length === 0 ? (
            <EmptyState icon="👤" text={t('Bu davrda sotuv yo‘q')} />
          ) : (
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ color: 'var(--text-faint)', fontSize: 12 }}>
                  <th style={{ padding: '6px 8px', textAlign: 'left' }}>{t('Kassir')}</th>
                  <th style={th}>{t('Cheklar')}</th>
                  <th style={th}>{t('Savdo')}</th>
                  <th style={th}>{t('O‘rtacha chek')}</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r, i) => (
                  <tr key={i} style={{ borderTop: '1px solid var(--border)' }}>
                    <td style={{ padding: '8px' }}><strong>{r.cashier}</strong></td>
                    <td style={td}>{r.receipts}</td>
                    <td style={{ ...td, fontWeight: 600 }}>{usd(r.net)}</td>
                    <td style={td}>{usd(r.avgReceipt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Loader>
      </div>
    </div>
  );
}

/* ──────────────────────────────────────────────────────────────
   Reorder queue (Buyurtma tavsiyalari) — AI-powered
────────────────────────────────────────────────────────────── */
function ReorderQueueSection({ t }) {
  const { data, loading, error, reload } = useApi(() => AiApi.reorderQueue(), []);
  const rows = data || [];
  return (
    <div className="card">
      <div className="card-head">
        <h2>🛒 {t('Buyurtma tavsiyalari')}</h2>
        <span className="hint">{rows.length} {t('ta')}</span>
      </div>
      <div className="card-pad">
        <Loader loading={loading} error={error} onRetry={reload}>
          {rows.length === 0 ? (
            <EmptyState icon="✅" text={t("Hammasi yetarli — buyurtma kerak emas")} />
          ) : (
            <>
              <p className="faint" style={{ fontSize: 13, marginBottom: 12 }}>
                {t("Oxirgi 30 kun ma'lumotiga asoslangan — har kuni 09:00 da Telegram'ga ham yuboriladi")}
              </p>
              <div className="table-wrap">
                <table className="tbl">
                  <thead>
                    <tr>
                      <th>{t('Mahsulot')}</th>
                      <th className="num">{t('Qoldiq')}</th>
                      <th className="num">{t('Kun qoldi')}</th>
                      <th className="num">{t('Tavsiya')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((f) => (
                      <tr key={f.productId}>
                        <td><strong>{f.name}</strong></td>
                        <td className="num mono"
                            style={{ color: f.currentQty === 0 ? '#dc2626' : '#f59e0b', fontWeight: 600 }}>
                          {f.currentQty}
                        </td>
                        <td className="num mono">{f.daysOfStock === null ? '∞' : f.daysOfStock}</td>
                        <td className="num mono" style={{ fontWeight: 600, color: '#16a34a' }}>
                          + {f.suggestedReorderQty} {t('dona')}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </Loader>
      </div>
    </div>
  );
}

/* ──────────────────────────────────────────────────────────────
   Slow movers + auto-promo recommendations
────────────────────────────────────────────────────────────── */
function SlowMoversSection({ t }) {
  const { data, loading, error, reload } = useApi(() => AiApi.slowMovers(), []);
  const rows = data || [];
  return (
    <div className="card">
      <div className="card-head">
        <h2>🐢 {t('Sekin sotilayotgan tovarlar')}</h2>
        <span className="hint">{rows.length} {t('ta')}</span>
      </div>
      <div className="card-pad">
        <Loader loading={loading} error={error} onRetry={reload}>
          {rows.length === 0 ? (
            <EmptyState icon="🚀" text={t('Hammasi yaxshi aylanyapti!')} />
          ) : (
            <>
              <p className="faint" style={{ fontSize: 13, marginBottom: 12 }}>
                {t("30 kun ichida kam sotilgan — aksiya o'tkazish foydali bo'lishi mumkin")}
              </p>
              <div className="table-wrap">
                <table className="tbl">
                  <thead>
                    <tr>
                      <th>{t('Mahsulot')}</th>
                      <th className="num">{t('Qoldiq')}</th>
                      <th className="num">{t('30 kunda')}</th>
                      <th className="num">{t('Tavsiya chegirma')}</th>
                      <th>{t('Sabab')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.slice(0, 30).map((s) => (
                      <tr key={s.productId}>
                        <td><strong>{s.name}</strong></td>
                        <td className="num mono">{s.currentQty}</td>
                        <td className="num mono faint">{s.soldLast30Days.toFixed(1)}</td>
                        <td className="num">
                          <span className="badge badge-aralash">-{s.suggestedDiscountPercent}%</span>
                        </td>
                        <td className="faint" style={{ fontSize: 12 }}>{s.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </Loader>
      </div>
    </div>
  );
}

/* ──────────────────────────────────────────────────────────────
   Profit by product (Mahsulot bo'yicha foyda)
────────────────────────────────────────────────────────────── */
function ProfitByProductSection({ t }) {
  const [from, setFrom] = useState(shiftIso(-30));
  const [to, setTo] = useState(todayIso());
  const [enabled, setEnabled] = useState(false);
  const { data, loading, error, reload } = useApi(
    () => (enabled ? ReportApi.profitByProduct({ from, to }) : Promise.resolve(null)),
    [from, to, enabled],
  );
  const rows = data || [];

  return (
    <div className="card">
      <div className="card-head">
        <h2>{t("Mahsulot bo'yicha foyda")}</h2>
        <span className="hint">{t("Davr ichidagi sotuvlar va foyda")}</span>
      </div>
      <div className="card-pad">
        <div className="flex" style={{ gap: 12, flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: 16 }}>
          <div className="field" style={{ margin: 0 }}>
            <label>{t('Dan')}</label>
            <input type="date" className="input" value={from} max={to}
                   onChange={(e) => { setFrom(e.target.value); setEnabled(false); }} />
          </div>
          <div className="field" style={{ margin: 0 }}>
            <label>{t('Gacha')}</label>
            <input type="date" className="input" value={to} min={from} max={todayIso()}
                   onChange={(e) => { setTo(e.target.value); setEnabled(false); }} />
          </div>
          <button className="btn btn-primary"
                  onClick={() => (enabled ? reload() : setEnabled(true))}>
            {loading ? <Spinner /> : t('Yuklash')}
          </button>
          {rows.length > 0 && (
            <ExportButton
              filename={`foyda-${from}-${to}`}
              rows={rows.map((r) => ({
                [t('Mahsulot')]: r.name,
                [t('Sotilgan dona')]: r.soldQty,
                [t('Daromad') + ' USD']: Number(r.revenueUsd),
                [t('Tannarx') + ' USD']: Number(r.costUsd),
                [t('Foyda') + ' USD']: Number(r.profitUsd),
                [t('Marj %')]: Number(r.marginPercent),
              }))}
            />
          )}
        </div>

        {error && <div className="empty">{error}</div>}
        {!enabled && !data && (
          <EmptyState icon="📊" text={t("Davr tanlang va «Yuklash» tugmasini bosing")} />
        )}
        {rows.length === 0 && enabled && !loading && (
          <EmptyState icon="📊" text={t("Bu davrda sotuvlar yo'q")} />
        )}
        {rows.length > 0 && (
          <div className="table-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th>#</th>
                  <th>{t('Mahsulot')}</th>
                  <th className="num">{t('Sotilgan dona')}</th>
                  <th className="num">{t('Daromad')}</th>
                  <th className="num">{t('Tannarx')}</th>
                  <th className="num">{t('Foyda')}</th>
                  <th className="num">{t('Marj')}</th>
                </tr>
              </thead>
              <tbody>
                {rows.slice(0, 50).map((r, i) => (
                  <tr key={r.productId}>
                    <td className="faint mono">{i + 1}</td>
                    <td><strong>{r.name}</strong></td>
                    <td className="num mono">{r.soldQty}</td>
                    <td className="num mono">{usd(r.revenueUsd)}</td>
                    <td className="num mono faint">{usd(r.costUsd)}</td>
                    <td className="num mono"
                        style={{ color: Number(r.profitUsd) >= 0 ? '#16a34a' : '#dc2626', fontWeight: 600 }}>
                      {usd(r.profitUsd)}
                    </td>
                    <td className="num mono">{Number(r.marginPercent).toFixed(1)}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

/* ──────────────────────────────────────────────────────────────
   Hourly sales heatmap
────────────────────────────────────────────────────────────── */
function HourlySalesSection({ t }) {
  const [from, setFrom] = useState(shiftIso(-30));
  const [to, setTo] = useState(todayIso());
  const [enabled, setEnabled] = useState(false);
  const { data, loading, error, reload } = useApi(
    () => (enabled ? ReportApi.hourlySales({ from, to }) : Promise.resolve(null)),
    [from, to, enabled],
  );
  const buckets = data || [];
  const max = buckets.reduce((m, b) => Math.max(m, b.count), 0) || 1;

  return (
    <div className="card">
      <div className="card-head">
        <h2>{t('Soatlik sotuvlar')}</h2>
        <span className="hint">{t("Qaysi soatlarda eng ko'p sotiladi")}</span>
      </div>
      <div className="card-pad">
        <div className="flex" style={{ gap: 12, flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: 16 }}>
          <div className="field" style={{ margin: 0 }}>
            <label>{t('Dan')}</label>
            <input type="date" className="input" value={from} max={to}
                   onChange={(e) => { setFrom(e.target.value); setEnabled(false); }} />
          </div>
          <div className="field" style={{ margin: 0 }}>
            <label>{t('Gacha')}</label>
            <input type="date" className="input" value={to} min={from} max={todayIso()}
                   onChange={(e) => { setTo(e.target.value); setEnabled(false); }} />
          </div>
          <button className="btn btn-primary"
                  onClick={() => (enabled ? reload() : setEnabled(true))}>
            {loading ? <Spinner /> : t('Yuklash')}
          </button>
        </div>

        {error && <div className="empty">{error}</div>}
        {!enabled && !data && (
          <EmptyState icon="🕐" text={t("Davr tanlang va «Yuklash» tugmasini bosing")} />
        )}
        {buckets.length > 0 && (
          <div className="hourly-grid">
            {buckets.map((b) => {
              const intensity = b.count / max;
              const bg = `rgba(34, 197, 94, ${0.08 + intensity * 0.72})`;
              return (
                <div key={b.hour} className="hourly-cell" style={{ background: bg }}>
                  <div className="hourly-hour">{String(b.hour).padStart(2, '0')}:00</div>
                  <div className="hourly-count">{b.count}</div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

/* ──────────────────────────────────────────────────────────────
   Price tags (PDF)
────────────────────────────────────────────────────────────── */
function PriceTagsSection({ t }) {
  const toast = useToast();
  const { data, loading } = useApi(() => ProductApi.list(), []);
  const products = data || [];
  const [selected, setSelected] = useState({});
  const [search, setSearch] = useState('');
  const [currency, setCurrency] = useState('UZS');

  const filtered = products.filter((p) =>
    !search || p.name.toLowerCase().includes(search.toLowerCase())
    || (p.barcode || '').includes(search));

  const selectedCount = Object.values(selected).filter(Boolean).length;

  const handleGenerate = () => {
    const list = products.filter((p) => selected[p.id]).map((p) => ({
      name: p.name,
      sku: p.barcode || `#${p.id}`,
      priceUzs: p.sellingPriceUzs,
      priceUsd: p.sellingPriceUsd,
    }));
    if (list.length === 0) {
      toast.error(t("Mahsulot tanlang"));
      return;
    }
    try {
      generatePriceTagsPdf(list, { currency });
      toast.success(t('PDF yuklab olindi'));
    } catch (err) {
      toast.error(err.message);
    }
  };

  return (
    <div className="card">
      <div className="card-head">
        <h2>{t('Narx yorliqlari')}</h2>
        <span className="hint">{t("A4 (24 dona / sahifa) — printerga jo'natish uchun")}</span>
      </div>
      <div className="card-pad">
        <div className="flex" style={{ gap: 12, marginBottom: 12, alignItems: 'center', flexWrap: 'wrap' }}>
          <input className="input" style={{ flex: 1, minWidth: 200 }}
                 placeholder={t('Mahsulot qidirish...')}
                 value={search} onChange={(e) => setSearch(e.target.value)} />
          <select className="input" value={currency} onChange={(e) => setCurrency(e.target.value)} style={{ width: 90 }}>
            <option value="UZS">UZS</option>
            <option value="USD">USD</option>
          </select>
          <span className="faint">{selectedCount} {t('tanlangan')}</span>
          <button className="btn btn-primary" disabled={selectedCount === 0} onClick={handleGenerate}>
            🏷️ {t('PDF yaratish')}
          </button>
        </div>

        <Loader loading={loading}>
          <div style={{ maxHeight: 320, overflow: 'auto', border: '1px solid var(--border, #e5e7eb)', borderRadius: 8 }}>
            {filtered.slice(0, 200).map((p) => (
              <label key={p.id} className="price-tag-row">
                <input type="checkbox" checked={!!selected[p.id]}
                       onChange={(e) => setSelected((s) => ({ ...s, [p.id]: e.target.checked }))} />
                <span style={{ flex: 1 }}>{p.name}</span>
                <span className="faint mono">{p.barcode || `#${p.id}`}</span>
                <span className="mono">
                  {currency === 'USD' ? `$${(p.sellingPriceUsd ?? 0).toFixed(2)}` : `${(p.sellingPriceUzs ?? 0).toLocaleString('en-US').replace(/,/g, ' ')} so'm`}
                </span>
              </label>
            ))}
          </div>
        </Loader>
      </div>
    </div>
  );
}

/* ──────────────────────────────────────────────────────────────
   Section 1 — Kunlik hisobot
────────────────────────────────────────────────────────────── */
function DailyReportSection({ t }) {
  const [date, setDate] = useState(todayIso());
  // null = not yet fetched; triggers fetch only on button press via reload().
  const [enabled, setEnabled] = useState(false);
  const toast = useToast();
  const [tgBusy, setTgBusy] = useState(false);

  const { data, loading, error, reload } = useApi(
    () => (enabled ? ReportApi.endOfDay(date) : Promise.resolve(null)),
    [date, enabled],
  );

  const handleLoad = () => {
    if (enabled) {
      reload();
    } else {
      setEnabled(true);
    }
  };

  const handleTelegram = async () => {
    setTgBusy(true);
    try {
      await ReportApi.sendTelegram(date);
      toast.success(t("Hisobot Telegramga yuborildi"));
    } catch (err) {
      toast.error(err.message || t("Xatolik yuz berdi"));
    } finally {
      setTgBusy(false);
    }
  };

  return (
    <div className="card">
      <div className="card-head">
        <h2>{t('Kunlik hisobot')}</h2>
        <span className="hint">{t('Tanlangan kun uchun moliyaviy natija')}</span>
      </div>
      <div className="card-pad">
        {/* Date + action row */}
        <div className="flex" style={{ gap: 12, flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: 20 }}>
          <div className="field" style={{ margin: 0 }}>
            <label>{t('Sana')}</label>
            <input
              className="input"
              type="date"
              value={date}
              max={todayIso()}
              onChange={(e) => {
                setDate(e.target.value);
                setEnabled(false);
              }}
            />
          </div>
          <button
            className="btn btn-primary"
            onClick={handleLoad}
            disabled={loading}
          >
            {loading ? <Spinner /> : t('Yuklash')}
          </button>
          <button
            className="btn"
            onClick={handleTelegram}
            disabled={tgBusy}
            style={{ marginLeft: 'auto' }}
          >
            {tgBusy ? <Spinner /> : '📨 ' + t('Telegram ga yuborish')}
          </button>
        </div>

        {/* Error state */}
        {error && (
          <div className="empty" style={{ padding: '16px 0' }}>
            <div className="e-ico">⚠️</div>
            <div className="e-text">{error}</div>
          </div>
        )}

        {/* Metric cards */}
        {!loading && !error && data && (
          <div className="metrics" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))' }}>
            <DayMetricCard
              tone="green"
              icon="💰"
              label={t('Jami savdo')}
              value={data.totalRevenue ?? data.totalSales ?? 0}
            />
            <DayMetricCard
              tone="red"
              icon="🧾"
              label={t('Xarajatlar')}
              value={data.totalExpenses ?? data.expenses ?? 0}
            />
            <DayMetricCard
              tone="blue"
              icon="📈"
              label={t('Sof foyda')}
              value={data.netProfit ?? data.profit ?? 0}
            />
            <DayMetricCard
              tone="amber"
              icon="🛒"
              label={t('Savdolar soni')}
              value={null}
              count={data.salesCount ?? data.transactionCount ?? data.count ?? 0}
            />
          </div>
        )}

        {/* Empty / not-fetched state */}
        {!loading && !error && !data && (
          <div className="empty" style={{ padding: '16px 0' }}>
            <div className="e-ico">📋</div>
            <div className="e-text">{t("Sana tanlang va «Yuklash» tugmasini bosing")}</div>
          </div>
        )}
      </div>
    </div>
  );
}

function DayMetricCard({ tone, icon, label, value, count }) {
  return (
    <div className={`metric-card tone-${tone}`}>
      <div className="mc-icon">{icon}</div>
      <div className="mc-body">
        <div className="mc-label">{label}</div>
        <div className="mc-value">
          {count !== undefined ? count : usd(value)}
        </div>
      </div>
    </div>
  );
}

/* ──────────────────────────────────────────────────────────────
   Section 2 — PDF Hisobotlar
────────────────────────────────────────────────────────────── */
function PdfReportSection({ t }) {
  const [from, setFrom] = useState(shiftIso(-30));
  const [to, setTo] = useState(todayIso());

  const openSalesPdf = () => {
    if (!from || !to) return;
    window.open(ReportApi.salesPdfUrl({ from, to }), '_blank', 'noreferrer');
  };

  const openInventoryPdf = () => {
    window.open(ReportApi.inventoryPdfUrl(), '_blank', 'noreferrer');
  };

  return (
    <div className="card">
      <div className="card-head">
        <h2>{t('PDF Hisobotlar')}</h2>
        <span className="hint">{t("Hisobotlarni PDF formatida yuklab oling")}</span>
      </div>
      <div className="card-pad">

        {/* Sales PDF */}
        <div className="card" style={{ marginBottom: 16, boxShadow: 'none', border: '1px solid var(--border)' }}>
          <div className="card-pad">
            <div className="section-label" style={{ marginBottom: 12 }}>
              {t('Savdo hisoboti (PDF)')}
            </div>
            <div className="flex" style={{ gap: 12, flexWrap: 'wrap', alignItems: 'flex-end' }}>
              <div className="field" style={{ margin: 0 }}>
                <label>{t('Dan')}</label>
                <input
                  className="input"
                  type="date"
                  value={from}
                  max={to || todayIso()}
                  onChange={(e) => setFrom(e.target.value)}
                />
              </div>
              <div className="field" style={{ margin: 0 }}>
                <label>{t('Gacha')}</label>
                <input
                  className="input"
                  type="date"
                  value={to}
                  min={from}
                  max={todayIso()}
                  onChange={(e) => setTo(e.target.value)}
                />
              </div>
              <button
                className="btn btn-primary"
                onClick={openSalesPdf}
                disabled={!from || !to}
              >
                ⬇ {t('Yuklab olish')}
              </button>
            </div>
          </div>
        </div>

        {/* Inventory PDF */}
        <div className="card" style={{ boxShadow: 'none', border: '1px solid var(--border)' }}>
          <div className="card-pad">
            <div className="section-label" style={{ marginBottom: 12 }}>
              {t('Ombor hisoboti (PDF)')}
            </div>
            <p className="faint" style={{ fontSize: 13, marginBottom: 12 }}>
              {t("Joriy ombor holati va tovar qoldiqlari")}
            </p>
            <button className="btn btn-primary" onClick={openInventoryPdf}>
              ⬇ {t('Yuklab olish')}
            </button>
          </div>
        </div>

      </div>
    </div>
  );
}
