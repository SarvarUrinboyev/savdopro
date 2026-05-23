package uz.barakat.market.auth;

/**
 * Thread-local holder for the currently-active shop id. Populated by
 * {@link TenantFilter} from the {@code X-Shop-Id} header on every API
 * request and read by services + Hibernate filters to scope queries.
 *
 * <p>If a request arrives without a shop id (e.g. login endpoint,
 * shop list endpoint), the value stays {@code null} and tenant-scoped
 * services should refuse to write data.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_SHOP = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setShopId(Long shopId) {
        CURRENT_SHOP.set(shopId);
    }

    public static Long currentShopId() {
        return CURRENT_SHOP.get();
    }

    public static long requireShopId() {
        Long id = CURRENT_SHOP.get();
        if (id == null) {
            throw new IllegalStateException(
                    "No active shop in request context — set X-Shop-Id header");
        }
        return id;
    }

    public static void clear() {
        CURRENT_SHOP.remove();
    }
}
