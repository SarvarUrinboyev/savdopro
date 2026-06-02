package uz.barakat.market.service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.barakat.market.repository.SupplierRepository;
import uz.barakat.market.service.ForecastService.ProductForecast;
import uz.barakat.market.telegram.TelegramService;

/**
 * Sends a daily "Bularni buyurtma qiling" digest of re-order candidates
 * to the operator's Telegram channel.
 *
 * <p>Trigger: cron at 09:00 server time (early-morning so the operator
 * can place orders before suppliers' open hours). Runs at startup too
 * if the digest is overdue, identical to {@link BackupService}'s pattern.
 *
 * <p>Doesn't actually create supplier Orders in the DB — that step
 * stays manual so the operator can adjust quantities, pick a supplier
 * and add notes. We just surface the recommendation.
 */
@Service
public class AutoPurchaseOrderService {

    private static final Logger log = LoggerFactory.getLogger(AutoPurchaseOrderService.class);

    private final ForecastService forecast;
    private final SupplierRepository suppliers;
    private final TelegramService telegram;
    private final GlobalScope globalScope;
    private final boolean enabled;

    public AutoPurchaseOrderService(
            ForecastService forecast,
            SupplierRepository suppliers,
            TelegramService telegram,
            GlobalScope globalScope,
            @Value("${auto-po.enabled:true}") boolean enabled) {
        this.forecast = forecast;
        this.suppliers = suppliers;
        this.telegram = telegram;
        this.globalScope = globalScope;
        this.enabled = enabled;
    }

    @PostConstruct
    public void onStartup() {
        if (enabled) {
            // Run once shortly after startup so the operator sees today's
            // recommendations on first launch.
            new Thread(() -> {
                try { Thread.sleep(30_000); } catch (InterruptedException ignore) { return; }
                runDigest("startup");
            }, "auto-po-startup").start();
        }
    }

    /** Daily 09:00. */
    @Scheduled(cron = "${auto-po.cron:0 0 9 * * *}")
    public void daily() {
        if (enabled) runDigest("scheduled");
    }

    public synchronized void runDigest(String trigger) {
        try {
            List<ProductForecast> queue = globalScope.call(forecast::reorderQueue);
            if (queue.isEmpty()) {
                log.info("Auto-PO [{}]: hech qanday qayta buyurtma kerak emas", trigger);
                return;
            }
            StringBuilder msg = new StringBuilder();
            msg.append("[*] BUGUNGI BUYURTMA TAVSIYASI (").append(queue.size()).append(" mahsulot):\n\n");
            queue.stream().limit(15).forEach(f -> {
                String days = f.daysOfStock() == null
                        ? "tugagan" : (f.daysOfStock() + " kun qoldi");
                msg.append(String.format(
                        "• %s — qoldiq %d, %s.%n   Tavsiya: %d dona buyurtma%n%n",
                        f.name(), f.currentQty(), days, f.suggestedReorderQty()));
            });
            if (queue.size() > 15) {
                msg.append("...va yana ").append(queue.size() - 15).append(" ta.\n");
            }
            msg.append("\nTo'liq ro'yxat: SavdoPRO → Hisobotlar → Buyurtma tavsiyalari");

            // If there are suppliers configured, mention each so the
            // operator quickly knows whom to call first.
            long supplierCount = suppliers.count();
            if (supplierCount > 0) {
                msg.append("\n\n").append(supplierCount).append(" ta ta'minotchi sozlangan.");
            }

            telegram.sendMessage(msg.toString());
            log.info("Auto-PO [{}]: Telegram'ga {} ta mahsulot yuborildi", trigger, queue.size());
        } catch (Exception ex) {
            log.warn("Auto-PO failed [{}]: {}", trigger, ex.getMessage(), ex);
        }
    }
}
