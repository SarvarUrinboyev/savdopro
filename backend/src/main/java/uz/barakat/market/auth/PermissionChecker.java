package uz.barakat.market.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Evaluates {@code RESOURCE:ACTION} permissions for the current request.
 *
 * <p>The effective permission set is minted by the License Server into the
 * JWT and copied into the security context as granted authorities by
 * {@link JwtAuthFilter}. Matching supports the same wildcards as the
 * server-side {@code PermissionService}:
 * <ul>
 *   <li>{@code *:*} — super-admin, matches everything</li>
 *   <li>{@code RESOURCE:*} — any action on a resource</li>
 *   <li>{@code *:ACTION} — an action on any resource</li>
 * </ul>
 *
 * <p>Exposed two ways: as the SpEL bean {@code @perm} (for
 * {@code @PreAuthorize("@perm.can('PRODUCTS','WRITE')")}) and as a static
 * helper used by the URL-based rules in {@code SecurityConfig}.
 */
@Component("perm")
public class PermissionChecker {

    /** SpEL entry point: {@code @perm.can('PRODUCTS','WRITE')}. */
    public boolean can(String resource, String action) {
        return hasPermission(SecurityContextHolder.getContext().getAuthentication(),
                resource, action);
    }

    /** True if {@code auth} carries a (possibly wildcard) authority granting
     *  {@code resource:action}. */
    public static boolean hasPermission(Authentication auth, String resource, String action) {
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (matches(ga.getAuthority(), resource, action)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String granted, String resource, String action) {
        if (granted == null) {
            return false;
        }
        int colon = granted.indexOf(':');
        // Skip non-permission authorities such as ROLE_ACCOUNT_OWNER.
        if (colon <= 0 || colon == granted.length() - 1) {
            return false;
        }
        String grantedResource = granted.substring(0, colon);
        String grantedAction = granted.substring(colon + 1);
        boolean resourceOk = "*".equals(grantedResource)
                || grantedResource.equalsIgnoreCase(resource);
        boolean actionOk = "*".equals(grantedAction)
                || grantedAction.equalsIgnoreCase(action);
        return resourceOk && actionOk;
    }
}
