package uz.barakat.market.telegram;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.config.AnomalyProperties;
import uz.barakat.market.repository.ShopRepository;
import uz.barakat.market.service.AnomalyMonitorService;

/**
 * Runs the deterministic anomaly scan on a schedule: a night pass after the
 * 22:00 evening report, and a midday pass so the dashboard banner / Telegram
 * also fire during the day. Crons come from {@code anomaly.*-cron}.
 *
 * <p>Unlike the other schedulers we set a <b>single</b> shop scope per
 * iteration rather than {@code GlobalScope}'s all-shops list: the detectors'
 * per-cashier baselines and the cash-till walk must be isolated to one shop,
 * and {@code TenantScopedEntity.@PrePersist} then tags new alerts with that
 * shop. Scope is always cleared in a {@code finally} so a failing shop never
 * leaks into the next.
 */
@Component
public class AnomalyScanScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnomalyScanScheduler.class);

    private final AnomalyMonitorService monitor;
    private final ShopRepository shops;
    private final AnomalyProperties props;

    public AnomalyScanScheduler(AnomalyMonitorService monitor, ShopRepository shops,
                                AnomalyProperties props) {
        this.monitor = monitor;
        this.shops = shops;
        this.props = props;
    }

    @Scheduled(cron = "${anomaly.scan-cron}")
    public void nightlyScan() {
        scanAllShops("nightly");
    }

    @Scheduled(cron = "${anomaly.intraday-scan-cron}")
    public void intradayScan() {
        scanAllShops("intraday");
    }

    private void scanAllShops(String label) {
        if (!props.enabled()) {
            return;
        }
        LocalDate day = LocalDate.now();
        int totalShops = 0;
        int totalAlerts = 0;
        for (Long shopId : shops.findAllIds()) {
            TenantContext.setShopId(shopId);
            try {
                totalAlerts += monitor.scanCurrentShop(day);
                totalShops++;
            } catch (RuntimeException ex) {
                log.warn("Anomaly scan ({}) failed for shop {}: {}", label, shopId, ex.toString());
            } finally {
                TenantContext.clear();
            }
        }
        log.info("Anomaly scan ({}) done: {} shops, {} new alerts", label, totalShops, totalAlerts);
    }
}
