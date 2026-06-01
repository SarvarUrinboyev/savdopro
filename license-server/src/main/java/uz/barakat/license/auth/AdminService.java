package uz.barakat.license.auth;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.license.auth.AdminDtos.AccountDetailResponse;
import uz.barakat.license.auth.AdminDtos.AdminAccountResponse;
import uz.barakat.license.auth.AdminDtos.AdminUserResponse;
import uz.barakat.license.auth.AdminDtos.CreateAccountRequest;
import uz.barakat.license.auth.AdminDtos.CreateUserRequest;
import uz.barakat.license.auth.AdminDtos.UpdateAccountRequest;
import uz.barakat.license.domain.Account;
import uz.barakat.license.domain.AppUser;
import uz.barakat.license.domain.UserRole;
import uz.barakat.license.exception.BadRequestException;
import uz.barakat.license.exception.NotFoundException;
import uz.barakat.license.repository.AccountRepository;
import uz.barakat.license.util.PhoneUtil;
import uz.barakat.license.repository.AppUserRepository;

/**
 * Super-admin operations: create / edit / block accounts, set or reset
 * passwords for any user. The controller layer enforces that the caller
 * has {@link UserRole#SUPER_ADMIN}; this service trusts that gate and
 * focuses on data integrity.
 */
@Service
@Transactional
public class AdminService {

    private final AccountRepository accounts;
    private final AppUserRepository users;
    private final AuditService audit;
    private final RefreshTokenService refreshTokens;
    // Cost 12 — see AdminBootstrap. All new passwords admin sets via
    // createAccount / createUser / resetPassword are hashed at the
    // stronger factor; old hashes verify normally because the cost
    // travels inside the hash.
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public AdminService(AccountRepository accounts, AppUserRepository users,
                        AuditService audit, RefreshTokenService refreshTokens) {
        this.accounts = accounts;
        this.users = users;
        this.audit = audit;
        this.refreshTokens = refreshTokens;
    }

    @Transactional(readOnly = true)
    public List<AdminAccountResponse> listAccounts() {
        // Pre-compute user counts in a single grouped query so we don't
        // fan out to one COUNT(*) per account in the row mapper. The
        // map lookup below is O(1).
        java.util.Map<Long, Long> counts = users.countsByAccountId();
        return accounts.findAll().stream()
                .sorted(Comparator.comparing(Account::getCreatedAt).reversed())
                .map(a -> toAccountResponseWithCount(a,
                        counts.getOrDefault(a.getId(), 0L).intValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountDetailResponse accountDetail(Long id) {
        Account a = accounts.findById(id)
                .orElseThrow(() -> NotFoundException.of("Akkaunt", id));
        List<AdminUserResponse> userList = users
                .findByAccountIdOrderByUsernameAsc(id).stream()
                .map(AdminService::toUserResponse)
                .toList();
        return new AccountDetailResponse(toAccountResponse(a), userList);
    }

    public AdminAccountResponse createAccount(CreateAccountRequest request) {
        String username = request.ownerUsername().trim().toLowerCase();
        // Cheap up-front check for the common case. The DB unique
        // constraint below is the *real* gate against a race between two
        // concurrent createAccount calls landing on the same username.
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new BadRequestException("Bu login band: " + username);
        }
        Account account = new Account();
        account.setName(request.name().trim());
        account.setContactPhone(PhoneUtil.normalize(request.contactPhone()));
        requireAccountPhoneUnique(account.getContactPhone(), null);
        account.setContactNote(blankToNull(request.contactNote()));
        account.setSubscriptionExpires(request.subscriptionExpires());
        account.setBlocked(false);
        Account saved = accounts.save(account);

        AppUser owner = new AppUser();
        owner.setUsername(username);
        owner.setPasswordHash(encoder.encode(request.ownerPassword()));
        owner.setFullName(blankToNull(request.ownerFullName()));
        owner.setRole(UserRole.ACCOUNT_OWNER);
        owner.setAccountId(saved.getId());
        try {
            users.save(owner);
        } catch (DataIntegrityViolationException ex) {
            // Race winner already grabbed this username — surface as a
            // friendly 400 instead of a 500 stack trace in the UI.
            throw new BadRequestException("Bu login band: " + username);
        }

        audit.record("ACCOUNT_CREATE", "ACCOUNT", saved.getId(), saved.getName(),
                "Owner: " + username);
        return toAccountResponse(saved);
    }

    public AdminAccountResponse updateAccount(Long id, UpdateAccountRequest request) {
        Account a = accounts.findById(id)
                .orElseThrow(() -> NotFoundException.of("Akkaunt", id));
        a.setName(request.name().trim());
        a.setContactPhone(PhoneUtil.normalize(request.contactPhone()));
        requireAccountPhoneUnique(a.getContactPhone(), id);
        a.setContactNote(blankToNull(request.contactNote()));
        a.setSubscriptionExpires(request.subscriptionExpires());
        // Phase 4.6 white-label — null-safe so old API clients that don't
        // send the brand block don't accidentally wipe a stored value.
        if (request.brandName() != null) {
            a.setBrandName(blankToNull(request.brandName()));
        }
        if (request.brandColorPrimary() != null) {
            a.setBrandColorPrimary(blankToNull(request.brandColorPrimary()));
        }
        if (request.brandColorSecondary() != null) {
            a.setBrandColorSecondary(blankToNull(request.brandColorSecondary()));
        }
        if (request.brandLogoUrl() != null) {
            a.setBrandLogoUrl(blankToNull(request.brandLogoUrl()));
        }
        if (request.brandFooterNote() != null) {
            a.setBrandFooterNote(blankToNull(request.brandFooterNote()));
        }
        Account saved = accounts.save(a);
        audit.record("ACCOUNT_UPDATE", "ACCOUNT", saved.getId(), saved.getName(),
                "subExpires=" + saved.getSubscriptionExpires());
        return toAccountResponse(saved);
    }

    public AdminAccountResponse setBlocked(Long id, boolean blocked) {
        Account a = accounts.findById(id)
                .orElseThrow(() -> NotFoundException.of("Akkaunt", id));
        if (id == 1L && blocked) {
            throw new BadRequestException("Super-admin akkauntini bloklash mumkin emas");
        }
        a.setBlocked(blocked);
        Account saved = accounts.save(a);
        // Block also nukes every active session for users of that
        // account — otherwise their already-issued access JWTs would
        // keep working until they expire (up to 1h).
        if (blocked) {
            refreshTokens.revokeAllForAccount(id);
        }
        audit.record(blocked ? "ACCOUNT_BLOCK" : "ACCOUNT_UNBLOCK",
                "ACCOUNT", id, saved.getName(), null);
        return toAccountResponse(saved);
    }

    public void deleteAccount(Long id) {
        if (id == 1L) {
            throw new BadRequestException("Super-admin akkauntini o'chirish mumkin emas");
        }
        Account a = accounts.findById(id)
                .orElseThrow(() -> NotFoundException.of("Akkaunt", id));
        String label = a.getName();
        refreshTokens.revokeAllForAccount(id);
        // ON DELETE CASCADE on app_users.account_id wipes the linked users.
        accounts.delete(a);
        audit.record("ACCOUNT_DELETE", "ACCOUNT", id, label, null);
    }

    public AdminUserResponse createUser(Long accountId, CreateUserRequest request) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> NotFoundException.of("Akkaunt", accountId));
        if (users.countByAccountId(accountId) >= account.getPlan().maxUsers()) {
            throw new BadRequestException(
                    "Tarif rejasi (" + account.getPlan() + ") bo'yicha foydalanuvchilar "
                            + "chegarasi (" + account.getPlan().maxUsers() + ") to'ldi. "
                            + "Rejani yangilang.");
        }
        String username = request.username().trim().toLowerCase();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new BadRequestException("Bu login band: " + username);
        }
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(request.password()));
        u.setFullName(blankToNull(request.fullName()));
        u.setRole(parseRole(request.role(), UserRole.SHOP_USER));
        u.setAccountId(accountId);
        try {
            AppUser saved = users.save(u);
            audit.record("USER_CREATE", "USER", saved.getId(), saved.getUsername(),
                    "role=" + saved.getRole() + ", accountId=" + accountId);
            return toUserResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            // Race winner already grabbed this username.
            throw new BadRequestException("Bu login band: " + username);
        }
    }

    public void resetPassword(Long userId, String newPassword) {
        AppUser u = users.findById(userId)
                .orElseThrow(() -> NotFoundException.of("Foydalanuvchi", userId));
        u.setPasswordHash(encoder.encode(newPassword));
        users.save(u);
        // Force a re-login on every device so the old refresh tokens
        // can't keep handing out access JWTs after the password change.
        refreshTokens.revokeAllForUser(userId);
        audit.record("USER_RESET_PASSWORD", "USER", userId, u.getUsername(), null);
    }

    public void deleteUser(Long userId) {
        AppUser u = users.findById(userId)
                .orElseThrow(() -> NotFoundException.of("Foydalanuvchi", userId));
        if (u.getRole() == UserRole.SUPER_ADMIN) {
            throw new BadRequestException("Super-admin foydalanuvchini o'chirish mumkin emas");
        }
        String label = u.getUsername();
        refreshTokens.revokeAllForUser(userId);
        users.delete(u);
        audit.record("USER_DELETE", "USER", userId, label, null);
    }

    // ------------------------------------------------------------ helpers

    /**
     * Single-account variant — still does one COUNT(*) lookup. Used by
     * create / update / setBlocked which only ever touch one account and
     * benefit from a fresh count.
     */
    private AdminAccountResponse toAccountResponse(Account a) {
        return toAccountResponseWithCount(a,
                users.findByAccountIdOrderByUsernameAsc(a.getId()).size());
    }

    private static AdminAccountResponse toAccountResponseWithCount(Account a, int userCount) {
        int days = a.getSubscriptionExpires() == null
                ? Integer.MAX_VALUE
                : (int) (a.getSubscriptionExpires().toEpochDay() - LocalDate.now().toEpochDay());
        boolean expired = a.getSubscriptionExpires() != null
                && a.getSubscriptionExpires().isBefore(LocalDate.now());
        return new AdminAccountResponse(
                a.getId(), a.getName(), a.getContactPhone(), a.getContactNote(),
                a.getSubscriptionExpires(), Math.max(0, days),
                a.isBlocked(), expired, a.getPlan().name(), userCount, a.getCreatedAt(),
                a.getEnabledModules());
    }

    /**
     * Replace the account's enabled-module list.
     *
     * The frontend sends a comma-separated key list (or null to mean
     * "all modules visible"). We trim, lowercase, de-dupe and re-join so
     * the stored value is normalized regardless of how the client built
     * the request. The set of valid keys is owned by the frontend; we
     * never reject unknown keys here so new modules can be rolled out
     * without an API change.
     */
    public AdminAccountResponse setModules(Long id, String enabledModulesCsv) {
        Account a = accounts.findById(id)
                .orElseThrow(() -> NotFoundException.of("Akkaunt", id));
        String normalized = normalizeModules(enabledModulesCsv);
        a.setEnabledModules(normalized);
        Account saved = accounts.save(a);
        audit.record("ACCOUNT_MODULES_SET", "ACCOUNT", id, saved.getName(),
                normalized == null ? "(all enabled)" : normalized);
        return toAccountResponse(saved);
    }

    private static String normalizeModules(String csv) {
        if (csv == null || csv.isBlank()) return null;
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        for (String raw : csv.split(",")) {
            String key = raw == null ? "" : raw.trim().toLowerCase();
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }
        return keys.isEmpty() ? null : String.join(",", keys);
    }

    private static AdminUserResponse toUserResponse(AppUser u) {
        return new AdminUserResponse(
                u.getId(), u.getUsername(), u.getFullName(),
                u.getRole().name(), u.getLastLoginAt(), u.getCreatedAt(),
                u.getPermissions());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Rejects a save when another account already uses this contact phone. */
    private void requireAccountPhoneUnique(String phone, Long selfId) {
        if (phone == null || phone.isBlank()) return;
        boolean taken = selfId == null
                ? accounts.existsByContactPhone(phone)
                : accounts.existsByContactPhoneAndIdNot(phone, selfId);
        if (taken) {
            throw new BadRequestException("Bu telefon raqam allaqachon boshqa akkauntga biriktirilgan: " + phone);
        }
    }

    private static UserRole parseRole(String name, UserRole fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            return UserRole.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
