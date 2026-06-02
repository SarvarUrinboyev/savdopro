package uz.barakat.market.service;

import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.repository.ShopRepository;

/**
 * Runs a unit of work with a tenant scope spanning EVERY shop.
 *
 * <p>Background jobs (scheduled reports, the owner/customer Telegram bots) have
 * no HTTP request, so {@link TenantContext} is unset. JPA {@code @Filter}-based
 * reads simply fall back to all rows there, but services that build their own
 * <em>native</em> queries (e.g. {@code ReportService.salesFor},
 * {@code ForecastService}) read {@link TenantContext#activeScope()} directly —
 * which is EMPTY off-request, so they return nothing (sales/forecast = 0).
 *
 * <p>Wrapping such a call in {@link #call}/{@link #run} sets the scope to all
 * shop ids first (matching the @Filter fall-back so the two stay consistent)
 * and always clears it afterwards.
 */
@Component
public class GlobalScope {

    private final ShopRepository shops;

    public GlobalScope(ShopRepository shops) {
        this.shops = shops;
    }

    public <T> T call(Supplier<T> body) {
        List<Long> ids = shops.findAllIds();
        TenantContext.setShopIds(ids);
        try {
            return body.get();
        } finally {
            TenantContext.clear();
        }
    }

    public void run(Runnable body) {
        call(() -> {
            body.run();
            return null;
        });
    }
}
