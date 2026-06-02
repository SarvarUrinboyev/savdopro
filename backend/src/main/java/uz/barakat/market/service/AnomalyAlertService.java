package uz.barakat.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.Sale;
import uz.barakat.market.domain.SaleItem;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.telegram.TelegramService;

/**
 * Smart owner alerts for suspicious POS activity. Currently: a sale line sold
 * BELOW its cost price — a pricing mistake or a cashier giving an unauthorised
 * discount, i.e. a direct loss. Best-effort + async, fired AFTER the sale is
 * saved, so it never blocks or breaks a checkout. A no-op when the owner
 * Telegram bot isn't configured.
 */
@Service
public class AnomalyAlertService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyAlertService.class);

    private final ProductRepository products;
    private final TelegramService telegram;

    public AnomalyAlertService(ProductRepository products, TelegramService telegram) {
        this.products = products;
        this.telegram = telegram;
    }

    /** Alerts the owner if any line on this sale was sold below its cost price. */
    public void checkBelowCost(Sale sale) {
        if (sale == null || !telegram.isConfigured()) {
            return;
        }
        try {
            List<String> losses = new ArrayList<>();
            BigDecimal totalLoss = BigDecimal.ZERO;
            for (SaleItem item : sale.getItems()) {
                if (item.getProductId() == null || item.getQuantity() <= 0) {
                    continue;
                }
                Product p = products.findById(item.getProductId()).orElse(null);
                if (p == null || p.getPurchasePrice() == null
                        || p.getPurchasePrice().signum() <= 0) {
                    continue; // unknown cost → can't judge
                }
                BigDecimal cost = p.getPurchasePrice();
                BigDecimal effUnit = item.getLineTotalUzs()
                        .divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.HALF_UP);
                if (effUnit.compareTo(cost) < 0) {
                    totalLoss = totalLoss.add(
                            cost.subtract(effUnit).multiply(BigDecimal.valueOf(item.getQuantity())));
                    losses.add("• " + item.getProductName() + " ×" + item.getQuantity()
                            + " — sotildi " + MoneyFormat.usd(effUnit)
                            + " / tannarx " + MoneyFormat.usd(cost));
                }
            }
            if (losses.isEmpty()) {
                return;
            }
            String msg = "⚠️ ZARARGA SOTUV — Chek #" + sale.getId() + "\n\n"
                    + String.join("\n", losses)
                    + "\n\nTaxminiy zarar: " + MoneyFormat.usd(totalLoss)
                    + "\nNarx yoki kassirni tekshiring.";
            CompletableFuture.runAsync(() -> telegram.sendMessage(msg));
        } catch (Exception ex) {
            log.warn("Below-cost alert failed for sale {}: {}",
                    sale.getId(), ex.toString());
        }
    }
}
