package uz.barakat.license.auth;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.license.domain.AppUser;
import uz.barakat.license.domain.UserRole;
import uz.barakat.license.exception.BadRequestException;
import uz.barakat.license.exception.NotFoundException;
import uz.barakat.license.repository.AppUserRepository;

/**
 * Resource-action ACL on top of the coarse {@link UserRole} enum.
 *
 * <p>Keys follow {@code RESOURCE:ACTION} where {@code RESOURCE} is one
 * of {@link #KNOWN_RESOURCES} and {@code ACTION} is one of
 * {@link #KNOWN_ACTIONS}. The wildcard {@code *} matches any value.
 * SUPER_ADMIN implicitly has {@code *:*} regardless of what's stored.
 *
 * <p>Per-user permissions are stored as a CSV on
 * {@code app_users.permissions}. A NULL value means "use the role's
 * defaults" ({@link #defaultsFor(UserRole)}). The user's effective set
 * is {@code defaults ∪ overrides} — we only ever ADD permissions through
 * this column; revocation under the role default is out of scope (use a
 * stricter role instead).
 */
@Service
@Transactional
public class PermissionService {

    static final Set<String> KNOWN_RESOURCES = Set.of(
            "ACCOUNTS", "USERS", "AUDIT", "REPORTS", "PRODUCTS", "ORDERS", "DEBTS");
    static final Set<String> KNOWN_ACTIONS = Set.of("READ", "WRITE");

    private final AppUserRepository users;

    public PermissionService(AppUserRepository users) {
        this.users = users;
    }

    /**
     * True if {@code user} has {@code permission} (e.g. {@code "USERS:WRITE"}).
     * SUPER_ADMIN always returns true. Wildcards on either side of the
     * colon match — {@code *:WRITE} means "WRITE on any resource".
     */
    @Transactional(readOnly = true)
    public boolean has(AppUser user, String permission) {
        if (user == null || permission == null) return false;
        if (user.getRole() == UserRole.SUPER_ADMIN) return true;
        String[] split = permission.split(":");
        if (split.length != 2) return false;
        String resource = split[0].toUpperCase(Locale.ROOT);
        String action = split[1].toUpperCase(Locale.ROOT);
        for (String granted : effective(user)) {
            String[] g = granted.split(":");
            if (g.length != 2) continue;
            boolean resourceMatch = "*".equals(g[0]) || g[0].equalsIgnoreCase(resource);
            boolean actionMatch = "*".equals(g[1]) || g[1].equalsIgnoreCase(action);
            if (resourceMatch && actionMatch) return true;
        }
        return false;
    }

    /** Convenience for the controller layer when only the user id is known. */
    @Transactional(readOnly = true)
    public boolean has(Long userId, String permission) {
        return users.findById(userId).map(u -> has(u, permission)).orElse(false);
    }

    /**
     * The union of role defaults and per-user overrides as an ordered set
     * (defaults first, then overrides in their stored order). Useful for
     * the {@code /me} endpoint so the desktop can hide UI affordances the
     * user can't action.
     */
    @Transactional(readOnly = true)
    public Set<String> effective(AppUser user) {
        Set<String> out = new LinkedHashSet<>(defaultsFor(user.getRole()));
        parse(user.getPermissions()).forEach(out::add);
        return out;
    }

    /**
     * Replace the per-user override CSV. Tokens are validated against
     * {@link #KNOWN_RESOURCES} / {@link #KNOWN_ACTIONS}; unknown values
     * are rejected so a typo can't silently grant nothing. Pass null /
     * blank to clear the override and fall back to role defaults.
     */
    public void setPermissions(Long userId, String csv) {
        // Validate the CSV first so a typo fails with a clear 400 before
        // we burn a DB lookup. NotFoundException is reserved for the
        // userId-doesn't-exist case below.
        String normalised = normaliseCsv(csv);
        AppUser u = users.findById(userId)
                .orElseThrow(() -> NotFoundException.of("Foydalanuvchi", userId));
        u.setPermissions(normalised);
        users.save(u);
    }

    // ============================================================ helpers

    /**
     * Coarse defaults per role. Kept conservative — anything beyond the
     * defaults must be granted explicitly through the override column.
     */
    public static Set<String> defaultsFor(UserRole role) {
        return switch (role) {
            case SUPER_ADMIN -> Set.of("*:*");
            case ACCOUNT_OWNER -> Set.of(
                    "ACCOUNTS:READ", "USERS:READ", "USERS:WRITE",
                    "AUDIT:READ", "REPORTS:READ");
            case SHOP_USER -> Set.of("REPORTS:READ");
        };
    }

    static Set<String> parse(String csv) {
        Set<String> out = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) return out;
        for (String raw : csv.split(",")) {
            String token = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if (!token.isEmpty()) out.add(token);
        }
        return out;
    }

    /**
     * Strip whitespace, dedupe, uppercase. Returns null when the input is
     * empty so the DB row goes back to "fall back to role defaults".
     */
    static String normaliseCsv(String csv) {
        if (csv == null || csv.isBlank()) return null;
        Set<String> tokens = new LinkedHashSet<>();
        for (String raw : csv.split(",")) {
            String token = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if (token.isEmpty()) continue;
            validateToken(token);
            tokens.add(token);
        }
        return tokens.isEmpty() ? null : String.join(",", tokens);
    }

    private static void validateToken(String token) {
        String[] split = token.split(":");
        if (split.length != 2) {
            throw new BadRequestException("Noto'g'ri permission shakli: " + token);
        }
        String resource = split[0];
        String action = split[1];
        if (!"*".equals(resource) && !KNOWN_RESOURCES.contains(resource)) {
            throw new BadRequestException("Noma'lum resurs: " + resource);
        }
        if (!"*".equals(action) && !KNOWN_ACTIONS.contains(action)) {
            throw new BadRequestException("Noma'lum amal: " + action);
        }
    }
}
