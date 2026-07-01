package uz.barakat.market.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.repository.PaymentRepository;
import uz.barakat.market.repository.SaleRepository;
import uz.barakat.market.service.LedgerEvents.PaymentRecorded;
import uz.barakat.market.service.LedgerEvents.SalePosted;
import uz.barakat.market.service.LedgerEvents.SaleRefunded;

/**
 * Real-time anomaly alerting: instead of waiting for the 14:00 / 22:30
 * scheduled scans, every committed sale / refund / payment triggers an
 * intraday scan of TODAY for that shop (debounced). The scan reuses the
 * whole {@link AnomalyDetectionService} rule set and
 * {@link AnomalyMonitorService}'s (shop, dedupe-key) persistence, so:
 *   - a till going negative or a refund burst alerts within seconds,
 *   - repeats are no-ops (dedup), the nightly scan stays a safety net,
 *   - Telegram + the dashboard banner light up through the existing path.
 *
 * <p>Shop resolution happens synchronously on the committing thread (its
 * tenant filter is still active — same pattern as WebhookEventListener);
 * the scan itself runs async with an explicitly-set {@link TenantContext},
 * mirroring AnomalyScanScheduler, so checkout latency is never affected.
 */
@Component
public class RealTimeAnomalyListener {

    private static final Logger log = LoggerFactory.getLogger(RealTimeAnomalyListener.class);

    private final AnomalyMonitorService monitor;
    private final SaleRepository sales;
    private final PaymentRepository payments;
    private final boolean enabled;
    private final long debounceMs;

    /** Per-shop last-scan clock for debouncing rush-hour receipt storms. */
    private final Map<Long, Long> lastScanAt = new ConcurrentHashMap<>();

    /** Async runner — swapped for a same-thread executor in tests. */
    private Executor executor = ForkJoinPool.commonPool();

    public RealTimeAnomalyListener(
            AnomalyMonitorService monitor, SaleRepository sales, PaymentRepository payments,
            @Value("${anomaly.realtime.enabled:${REALTIME_ANOMALY_ENABLED:true}}") boolean enabled,
            @Value("${anomaly.realtime.debounce-seconds:60}") long debounceSeconds) {
        this.monitor = monitor;
        this.sales = sales;
        this.payments = payments;
        this.enabled = enabled;
        this.debounceMs = Math.max(0, debounceSeconds) * 1000L;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSale(SalePosted ev) {
        if (!enabled) return;
        try {
            sales.findById(ev.saleId()).ifPresent(s -> maybeScan(s.getShopId()));
        } catch (RuntimeException ex) {
            log.debug("Realtime anomaly: sale {} lookup failed: {}", ev.saleId(), ex.toString());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRefund(SaleRefunded ev) {
        if (!enabled) return;
        maybeScan(ev.shopId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPayment(PaymentRecorded ev) {
        if (!enabled) return;
        try {
            payments.findById(ev.paymentId()).ifPresent(p -> maybeScan(p.getShopId()));
        } catch (RuntimeException ex) {
            log.debug("Realtime anomaly: payment {} lookup failed: {}", ev.paymentId(), ex.toString());
        }
    }

    /** Debounce, then scan today for the shop on a background thread. */
    void maybeScan(Long shopId) {
        if (shopId == null) return;
        long now = System.currentTimeMillis();
        Long prev = lastScanAt.get(shopId);
        if (prev != null && now - prev < debounceMs) {
            return;
        }
        // Claim the slot before going async so a burst spawns one scan, not N.
        lastScanAt.put(shopId, now);
        executor.execute(() -> scanNow(shopId));
    }

    void scanNow(Long shopId) {
        TenantContext.setShopId(shopId);
        try {
            int created = monitor.scanCurrentShop(LocalDate.now());
            if (created > 0) {
                log.info("Realtime anomaly scan: shop {} -> {} new alert(s)", shopId, created);
            }
        } catch (RuntimeException ex) {
            log.warn("Realtime anomaly scan failed for shop {}: {}", shopId, ex.toString());
        } finally {
            TenantContext.clear();
        }
    }

    void setExecutorForTests(Executor executor) {
        this.executor = executor;
    }
}
