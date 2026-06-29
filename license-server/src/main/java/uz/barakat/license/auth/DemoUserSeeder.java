package uz.barakat.license.auth;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.license.domain.AppUser;
import uz.barakat.license.domain.UserRole;
import uz.barakat.license.repository.AppUserRepository;

/**
 * Guarded, idempotent demo login users so a founder can actually log in to a
 * dev/staging stack and land on the seeded shop data created by the backend's
 * {@code DemoDataSeeder}. The {@code accountId} on each user matches the
 * backend's reserved demo accounts (90001 / 90002), which is the only link the
 * two services need — the JWT carries {@code accountId}, the backend resolves
 * the shop from it.
 *
 * <h2>Safety</h2>
 * <ul>
 *   <li>Runs ONLY when {@code app.demo-seed.enabled=true} ({@code ALLOW_DEMO_SEED}
 *       env) and the active profile is not {@code prod}.</li>
 *   <li>The shared demo password comes from the {@code DEMO_SEED_PASSWORD} env
 *       var — never hardcoded. If it is unset or fails the same strength rule as
 *       the super-admin bootstrap, user creation is skipped with a WARN (it never
 *       throws, so it can't block startup).</li>
 *   <li>Idempotent: each user is created only if its username is free; the demo
 *       account rows use an explicit-id {@code INSERT … WHERE NOT EXISTS} guard.</li>
 * </ul>
 */
@Component
public class DemoUserSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private static final long ACCOUNT_A = 90_001L;
    private static final long ACCOUNT_B = 90_002L;
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AppUserRepository users;
    private final JdbcTemplate jdbc;
    private final Environment env;
    private final boolean enabledProp;
    private final String demoPassword;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public DemoUserSeeder(AppUserRepository users, JdbcTemplate jdbc, Environment env,
                          @Value("${app.demo-seed.enabled:false}") boolean enabledProp,
                          @Value("${DEMO_SEED_PASSWORD:}") String demoPassword) {
        this.users = users;
        this.jdbc = jdbc;
        this.env = env;
        this.enabledProp = enabledProp;
        this.demoPassword = demoPassword;
    }

    @PostConstruct
    @Transactional
    public void ensureDemoUsers() {
        if (!enabled()) {
            return;
        }
        if (isWeak(demoPassword)) {
            log.warn("ALLOW_DEMO_SEED is on but DEMO_SEED_PASSWORD is unset/weak "
                    + "(need {}+ chars with a letter AND a digit). Skipping demo-user seed.",
                    MIN_PASSWORD_LENGTH);
            return;
        }
        LocalDate subUntil = LocalDate.now().plusYears(1);
        upsertAccount(ACCOUNT_A, "DEMO — Barokat Savdo", subUntil);
        upsertAccount(ACCOUNT_B, "DEMO — Raqobatchi Do'kon", subUntil);

        int created = 0;
        created += ensureUser("demo_owner", "Barokat Demo — Egasi", UserRole.ACCOUNT_OWNER, ACCOUNT_A);
        created += ensureUser("demo_kassir", "Barokat Demo — Kassir", UserRole.SHOP_USER, ACCOUNT_A);
        created += ensureUser("demo_owner_b", "Raqobatchi Demo — Egasi", UserRole.ACCOUNT_OWNER, ACCOUNT_B);
        if (created > 0) {
            log.info("Seeded {} demo login user(s): demo_owner / demo_kassir / demo_owner_b "
                    + "(password = DEMO_SEED_PASSWORD).", created);
        }
    }

    boolean enabled() {
        List<String> profiles = Arrays.asList(env.getActiveProfiles());
        if (profiles.contains("prod")) {
            return false;
        }
        return enabledProp;
    }

    private int ensureUser(String username, String fullName, UserRole role, long accountId) {
        if (users.findByUsernameIgnoreCase(username).isPresent()) {
            return 0;
        }
        AppUser u = new AppUser();
        u.setUsername(username.toLowerCase(Locale.ROOT));
        u.setPasswordHash(encoder.encode(demoPassword));
        u.setFullName(fullName);
        u.setRole(role);
        u.setAccountId(accountId);
        users.save(u);
        return 1;
    }

    private void upsertAccount(long id, String name, LocalDate subUntil) {
        jdbc.update(
                "INSERT INTO accounts (id, name, plan, subscription_expires, created_at) "
                + "SELECT ?, ?, 'STANDARD', ?, now() "
                + "WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE id = ?)",
                id, name, java.sql.Date.valueOf(subUntil), id);
    }

    /** Same strength rule as the super-admin bootstrap. */
    private static boolean isWeak(String pw) {
        if (pw == null || pw.length() < MIN_PASSWORD_LENGTH) {
            return true;
        }
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < pw.length(); i++) {
            char c = pw.charAt(i);
            if (Character.isLetter(c)) {
                letter = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            }
        }
        return !(letter && digit);
    }
}
