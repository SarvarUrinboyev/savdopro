package uz.barakat.market.auth;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.barakat.market.domain.Shop;
import uz.barakat.market.repository.ShopRepository;

/**
 * Reads the {@code X-Shop-Id} header and parks tenant scope on the
 * thread via {@link TenantContext}.
 *
 * <ul>
 *   <li>Numeric value → single-shop mode, {@link TenantContext#setShopId}</li>
 *   <li>{@code ALL} → consolidated mode for the main-shop owner:
 *       resolves every shop in the caller's account and calls
 *       {@link TenantContext#setShopIds}</li>
 * </ul>
 *
 * <p>Runs <strong>after</strong> {@link JwtAuthFilter} so the
 * accountId / role attributes are already set when we validate the
 * incoming tenant header.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    /**
     * Sentinel id used when {@code X-Shop-Id: ALL} is requested but the
     * caller's account currently owns zero shops. Activating
     * {@code accountFilter} with this id guarantees every tenant-scoped
     * query returns no rows instead of silently falling through to an
     * unfiltered scan.
     */
    private static final Long EMPTY_TENANT_SENTINEL = -1L;

    private final ShopRepository shops;
    private final MeterRegistry metrics;

    public TenantFilter(ShopRepository shops, MeterRegistry metrics) {
        this.shops = shops;
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String raw = request.getHeader("X-Shop-Id");
        String trimmed = raw == null ? null : raw.trim();
        Long accountId = (Long) request.getAttribute(JwtAuthFilter.ATTR_ACCOUNT_ID);
        String role = (String) request.getAttribute(JwtAuthFilter.ATTR_ROLE);
        String path = request.getRequestURI();
        String remote = request.getRemoteAddr();

        try {
            if (trimmed != null && "ALL".equalsIgnoreCase(trimmed)) {
                // Consolidated "Hamma do'konlar" mode.
                if (accountId == null) {
                    // Unauthenticated callers must never reach the consolidated
                    // scope — log it and fall through with no tenant set; Spring
                    // Security will reject the call shortly afterwards.
                    log.warn("X-Shop-Id=ALL on unauthenticated request path={} remote={} — ignoring",
                            path, remote);
                } else if ("SHOP_USER".equals(role)) {
                    // Cashiers belong to a single shop; allowing ALL would let
                    // them see sibling shops in the same account. Reject hard.
                    log.warn("SHOP_USER attempted X-Shop-Id=ALL accountId={} path={} remote={} — denied",
                            accountId, path, remote);
                    metrics.counter("security.tenant.violation", "reason", "consolidated_denied").increment();
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"code\":\"FORBIDDEN\",\"shop\":true,\"message\":\"Hamma do'konlar rejimi sizga ruxsat etilmagan\"}");
                    return;
                } else {
                    List<Long> ids = shops
                            .findByAccountIdOrderByMainDescNameAsc(accountId).stream()
                            .map(Shop::getId)
                            .toList();
                    if (ids.isEmpty()) {
                        // The owner sent ALL but owns no shops yet. Activate the
                        // filter with a sentinel id so nothing matches — never
                        // leave the tenant unset, that would mean "see everything".
                        log.warn("X-Shop-Id=ALL accountId={} expanded to 0 shops path={} — using sentinel",
                                accountId, path);
                        TenantContext.setShopIds(List.of(EMPTY_TENANT_SENTINEL));
                    } else {
                        TenantContext.setShopIds(ids);
                    }
                }
            } else {
                Long shopId = parseShopId(trimmed);
                if (shopId != null && shopId <= 0L) {
                    // Reject non-positive ids outright; ids are BIGSERIAL and
                    // always positive, so anything else is a tampering attempt.
                    log.warn("Rejecting non-positive X-Shop-Id={} accountId={} path={} remote={}",
                            shopId, accountId, path, remote);
                    shopId = null;
                }
                if (shopId != null) {
                    if (accountId == null) {
                        // No authenticated account on the request — never trust a
                        // shop header from an anonymous caller. Drop it; Spring
                        // Security rejects the (necessarily /api) call right after.
                        log.warn("X-Shop-Id={} on unauthenticated request path={} remote={} — ignoring",
                                shopId, path, remote);
                        shopId = null;
                    } else if (!shops.existsByIdAndAccountId(shopId, accountId)) {
                        // Cross-tenant attempt: the shop is owned by a different
                        // account (or doesn't exist). Refuse — a tenant must never
                        // be able to scope queries to a shop it does not own.
                        log.warn("X-Shop-Id={} not owned by accountId={} path={} remote={} — denied",
                                shopId, accountId, path, remote);
                        metrics.counter("security.tenant.violation", "reason", "cross_tenant").increment();
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write(
                                "{\"code\":\"FORBIDDEN\",\"shop\":true,\"message\":\"Bu do'kon sizning akkauntingizga tegishli emas\"}");
                        return;
                    }
                }
                if (shopId == null && accountId != null && isTenantScopedPath(path)) {
                    // Phase 2 fallback: the desktop's first-login flow doesn't
                    // know an X-Shop-Id yet (the frontend has just received its
                    // JWT and hasn't fetched /api/shops). Rather than rejecting
                    // the request and dumping the user into an error state,
                    // resolve their account's main shop here and lazily create
                    // "Asosiy do'kon" if none exists.
                    shopId = shops.findFirstByAccountIdAndMainTrue(accountId)
                            .map(Shop::getId)
                            .orElseGet(() -> {
                                Shop bootstrap = new Shop();
                                bootstrap.setAccountId(accountId);
                                bootstrap.setName("Asosiy do'kon");
                                bootstrap.setMain(true);
                                Shop saved = shops.save(bootstrap);
                                log.info("Lazy-bootstrapped main shop id={} for accountId={} (path={})",
                                        saved.getId(), accountId, path);
                                return saved.getId();
                            });
                }
                TenantContext.setShopId(shopId);
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private static Long parseShopId(String header) {
        if (header == null || header.isBlank()) return null;
        try {
            return Long.parseLong(header);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * True for paths whose handlers expect a populated tenant context.
     * Login, the current-session call, shop listing/CRUD and health
     * checks are intentionally tenant-agnostic and should not warn.
     */
    private static boolean isTenantScopedPath(String path) {
        if (path == null || !path.startsWith("/api/")) return false;
        if (path.equals("/api/auth/login")) return false;
        if (path.equals("/api/auth/me")) return false;
        if (path.startsWith("/api/shops")) return false;
        if (path.startsWith("/api/health")) return false;
        if (path.startsWith("/api/admin")) return false;
        return true;
    }
}
