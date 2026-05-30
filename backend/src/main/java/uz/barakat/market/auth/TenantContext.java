package uz.barakat.market.auth;

import java.util.List;

/**
 * Thread-local holder for the request's tenant scope.
 *
 * <ul>
 *   <li>{@link #currentShopId()} returns the single active shop when
 *       the user is operating on one specific shop (the common case).</li>
 *   <li>{@link #currentShopIds()} returns the full set of shop ids the
 *       request can see — used in the consolidated "Hamma do'konlar"
 *       view where the main-shop owner aggregates every sub-shop.</li>
 * </ul>
 *
 * <p>Exactly one of the two is set per request. {@code TenantFilter}
 * populates these from the {@code X-Shop-Id} header on every API call
 * (a numeric id for single-shop mode, {@code ALL} for consolidated).
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_SHOP = new ThreadLocal<>();
    private static final ThreadLocal<List<Long>> CURRENT_SHOP_IDS = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setShopId(Long shopId) {
        CURRENT_SHOP.set(shopId);
        CURRENT_SHOP_IDS.remove();
    }

    public static void setShopIds(List<Long> shopIds) {
        CURRENT_SHOP_IDS.set(shopIds);
        CURRENT_SHOP.remove();
    }

    public static Long currentShopId() {
        return CURRENT_SHOP.get();
    }

    public static List<Long> currentShopIds() {
        return CURRENT_SHOP_IDS.get();
    }

    /**
     * The shop ids in effect for the current request as a flat list: the
     * consolidated set when in "Hamma do'konlar" mode, otherwise the single
     * active shop, otherwise empty. Used to scope <em>native</em> queries,
     * which the Hibernate {@code @Filter} cannot rewrite.
     */
    public static List<Long> activeScope() {
        List<Long> ids = CURRENT_SHOP_IDS.get();
        if (ids != null && !ids.isEmpty()) {
            return ids;
        }
        Long single = CURRENT_SHOP.get();
        return single != null ? List.of(single) : List.of();
    }

    /** {@code true} when the request is in consolidated "Hamma do'konlar" mode. */
    public static boolean isConsolidated() {
        return CURRENT_SHOP_IDS.get() != null;
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
        CURRENT_SHOP_IDS.remove();
    }
}
