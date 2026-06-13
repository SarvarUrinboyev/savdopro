package uz.barakat.license.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * A user that can log in to SavdoPRO. {@code passwordHash} stores a
 * BCrypt hash — the plaintext password is never persisted. The user
 * always belongs to exactly one {@link Account} (super-admins belong
 * to account id 1).
 */
@Entity
@Table(name = "app_users")
@Getter
@Setter
public class AppUser extends BaseEntity {

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(name = "full_name", length = 180)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * Base32-encoded TOTP shared secret (Phase 4.5). Null = 2FA never
     * set up. The secret is written on the first setup() call but
     * {@link #totpEnabled} flips only after the user confirms the
     * first code from their authenticator app — that prevents an
     * interrupted setup from locking anyone out.
     */
    @Column(name = "totp_secret", length = 64)
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    /**
     * Telegram numeric user id when the user has linked their Telegram
     * account via the Login Widget. Null = not linked. Unique across the
     * table (V7 migration), so a Telegram account can only point at one
     * SavdoPRO user.
     */
    @Column(name = "telegram_id", unique = true)
    private Long telegramId;

    /**
     * Phone number used for SMS-code login (Phase 4.5, V8 migration).
     * Stored verbatim — normalisation (E.164 etc.) is done by the
     * service layer. Null = SMS login not enabled for this user. Unique
     * across the table so a phone number maps to one SavdoPRO identity.
     */
    @Column(name = "phone", unique = true, length = 20)
    private String phone;

    /**
     * Comma-separated "RESOURCE:ACTION" tokens granting access beyond
     * (or restricting under) the role defaults (Phase 4.5, V9 migration).
     * NULL = fall back to {@link UserRole}'s default permission set.
     * See {@link uz.barakat.license.auth.PermissionService}.
     */
    @Column(name = "permissions", length = 500)
    private String permissions;

    /**
     * Newline-joined SHA-256 hashes of one-time TOTP recovery codes (V13).
     * Generated when 2FA is enabled and shown to the user in plaintext once;
     * a code is removed from this set when used at login. Null = none issued.
     */
    @Column(name = "totp_backup_codes", length = 1000)
    private String totpBackupCodes;
}
