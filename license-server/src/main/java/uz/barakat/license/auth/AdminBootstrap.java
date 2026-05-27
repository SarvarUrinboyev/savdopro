package uz.barakat.license.auth;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.license.domain.AppUser;
import uz.barakat.license.domain.UserRole;
import uz.barakat.license.repository.AppUserRepository;

/**
 * Bootstraps the super-admin user on first launch so the desktop login
 * screen actually has a valid account to authenticate against.
 *
 * <p>The credentials are read from {@code application-local.properties}
 * (gitignored) so they never end up in the repo. If the configured user
 * doesn't exist yet it is created; if it exists, the password is left
 * alone (manual change via SQL / admin API later).
 *
 * <h2>Weak-password gate</h2>
 * Before creating the seeded super-admin, we fail closed if the
 * configured password is unset, shorter than 8 chars, or matches one of
 * the well-known weak defaults in {@link #WEAK_DEFAULT_PASSWORDS}. The
 * operator must supply a strong value via the
 * {@code SAVDOPRO_ADMIN_PASSWORD} env var.
 * <p>Setting {@code SAVDOPRO_ALLOW_DEV_ADMIN=true} opts in to the weak
 * default with a loud WARN and is intended for local development only.
 * Mirrors the {@code SAVDOPRO_ALLOW_DEV_SECRET} flag enforced by
 * {@link JwtService}.
 */
@Component
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    /**
     * Well-known weak defaults we explicitly reject. Stored lowercase;
     * the configured password is compared after folding to {@link Locale#ROOT}
     * lowercase. Entries shorter than {@link #MIN_PASSWORD_LENGTH} are
     * also caught by the length check, but we list them anyway so the
     * rejection holds if that threshold is ever lowered.
     */
    static final Set<String> WEAK_DEFAULT_PASSWORDS = Set.of(
            "admin",
            "admin123",
            "password",
            "12345678",
            "qwerty",
            "savdopro",
            "barakat"
    );

    /** Minimum acceptable length for the bootstrapped admin password. */
    static final int MIN_PASSWORD_LENGTH = 8;

    private final AppUserRepository users;
    private final String adminUsername;
    private final String adminPassword;
    private final String adminFullName;
    private final boolean allowDevDefaults;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdminBootstrap(AppUserRepository users,
                          @Value("${savdopro.admin.username:admin}") String username,
                          @Value("${savdopro.admin.password:admin123}") String password,
                          @Value("${savdopro.admin.full-name:Super Admin}") String fullName,
                          @Value("${SAVDOPRO_ALLOW_DEV_ADMIN:false}") boolean allowDevDefaults) {
        this.users = users;
        this.adminUsername = username;
        this.adminPassword = password;
        this.adminFullName = fullName;
        this.allowDevDefaults = allowDevDefaults;
    }

    @PostConstruct
    @Transactional
    public void ensureAdmin() {
        if (users.existsByUsernameIgnoreCase(adminUsername)) {
            return;
        }
        // Fail closed before persisting a weak super-admin. If the operator
        // hasn't supplied a strong SAVDOPRO_ADMIN_PASSWORD, refuse to start
        // unless SAVDOPRO_ALLOW_DEV_ADMIN=true explicitly opts in to the
        // weak default (intended for local development only).
        boolean weak = adminPassword == null
                || adminPassword.length() < MIN_PASSWORD_LENGTH
                || WEAK_DEFAULT_PASSWORDS.contains(adminPassword.toLowerCase(Locale.ROOT));
        if (weak) {
            if (!allowDevDefaults) {
                throw new IllegalStateException(
                        "REFUSING TO START: savdopro.admin.password is unset, shorter than "
                                + MIN_PASSWORD_LENGTH + " chars, or matches a well-known weak "
                                + "default. Generate a strong password and pass it via the "
                                + "SAVDOPRO_ADMIN_PASSWORD env var. To explicitly allow the "
                                + "weak default for local development, set "
                                + "SAVDOPRO_ALLOW_DEV_ADMIN=true.");
            }
            log.warn("=================================================================");
            log.warn("  Admin password is WEAK — DO NOT USE IN PRODUCTION.");
            log.warn("  SAVDOPRO_ALLOW_DEV_ADMIN=true is set; booting anyway.");
            log.warn("  Set SAVDOPRO_ADMIN_PASSWORD to a strong value before deploy.");
            log.warn("=================================================================");
        }
        AppUser u = new AppUser();
        u.setUsername(adminUsername.toLowerCase());
        u.setPasswordHash(encoder.encode(adminPassword));
        u.setFullName(adminFullName);
        u.setRole(UserRole.SUPER_ADMIN);
        u.setAccountId(1L); // seeded super-admin account
        users.save(u);
        log.info("Bootstrapped super-admin user '{}' (change the password!)", adminUsername);
    }
}
