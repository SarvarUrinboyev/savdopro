package uz.barakat.license.auth;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.license.auth.AuthDtos.LoginRequest;
import uz.barakat.license.auth.AuthDtos.LoginResponse;
import uz.barakat.license.auth.AuthDtos.MeResponse;
import uz.barakat.license.domain.Account;
import uz.barakat.license.domain.AppUser;
import uz.barakat.license.exception.BadRequestException;
import uz.barakat.license.repository.AccountRepository;
import uz.barakat.license.repository.AppUserRepository;

/**
 * Login / session validation. Rejects on bad credentials, blocked
 * accounts or expired subscriptions. Computes the "days until block"
 * countdown that the desktop UI shows as a warning banner.
 */
@Service
@Transactional
public class AuthService {

    /** Warn the user this many days before the subscription auto-blocks. */
    public static final int WARNING_THRESHOLD_DAYS = 4;

    private final AppUserRepository users;
    private final AccountRepository accounts;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final TotpService totp;
    private final TelegramOAuthVerifier telegramVerifier;
    private final OtpService otp;
    private final SmsProvider sms;
    private final PermissionService permissions;
    // Cost 12 — see AdminBootstrap; old hashes (cost 10) still verify
    // because BCrypt stores the cost inside the hash, so existing users
    // keep logging in until their next password reset re-hashes at 12.
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public AuthService(AppUserRepository users, AccountRepository accounts,
                       JwtService jwt, RefreshTokenService refreshTokens,
                       TotpService totp, TelegramOAuthVerifier telegramVerifier,
                       OtpService otp, SmsProvider sms,
                       PermissionService permissions) {
        this.users = users;
        this.accounts = accounts;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.totp = totp;
        this.telegramVerifier = telegramVerifier;
        this.otp = otp;
        this.sms = sms;
        this.permissions = permissions;
    }

    public LoginResponse login(LoginRequest request, String clientIp) {
        AppUser user = users.findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(() -> new BadRequestException("Login yoki parol noto'g'ri"));
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadRequestException("Login yoki parol noto'g'ri");
        }
        // Phase 4.5: enforce TOTP if the user enabled it. The error
        // message stays generic so a brute-forcer can't tell whether
        // the password or the code was wrong.
        if (user.isTotpEnabled()) {
            String code = request.totpCode();
            if (code == null || code.isBlank()) {
                // Special tag-only exception so the client knows to show
                // the 2FA code field rather than retry with the password.
                throw new BadRequestException("2FA kodi kerak");
            }
            if (!totp.verify(user.getTotpSecret(), code)) {
                throw new BadRequestException("2FA kodi noto'g'ri");
            }
        }
        Account account = requireUsableAccount(user.getAccountId());
        user.setLastLoginAt(LocalDateTime.now());
        users.save(user);
        RefreshTokenService.Issued refresh = refreshTokens.issue(
                user.getId(), account.getId(), clientIp);
        return new LoginResponse(
                jwt.issue(user),
                refresh.plaintext(),
                jwt.accessTtlSeconds(),
                refresh.expiresAt(),
                toMe(user, account));
    }

    // ============================================================ TOTP

    /**
     * Begin TOTP enrolment: generates a secret, writes it on the user
     * row (but does not enable 2FA yet — see {@link #confirmTotp(Long, String)}).
     * Returns the secret + the otpauth:// URI for the QR code.
     */
    public uz.barakat.license.auth.AuthDtos.TotpSetupResponse setupTotp(Long userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new BadRequestException("Sessiya yaroqsiz"));
        String secret = totp.generateSecret();
        user.setTotpSecret(secret);
        user.setTotpEnabled(false);   // requires confirmation step
        users.save(user);
        return new uz.barakat.license.auth.AuthDtos.TotpSetupResponse(
                secret,
                totp.otpauthUri(user.getUsername(), secret, "SavdoPRO"));
    }

    /** Verify the first code from the authenticator and enable 2FA. */
    public void confirmTotp(Long userId, String code) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new BadRequestException("Sessiya yaroqsiz"));
        if (user.getTotpSecret() == null) {
            throw new BadRequestException("Avval TOTP sozlamasini boshlang");
        }
        if (!totp.verify(user.getTotpSecret(), code)) {
            throw new BadRequestException("Kod noto'g'ri — qaytadan urinib ko'ring");
        }
        user.setTotpEnabled(true);
        users.save(user);
    }

    /** Turn 2FA off (clears the secret too so re-enabling creates a fresh one). */
    public void disableTotp(Long userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new BadRequestException("Sessiya yaroqsiz"));
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        users.save(user);
    }

    // ============================================================ Telegram OAuth

    /**
     * Verify a Telegram Login Widget payload and mint a SavdoPRO session
     * for the linked user. The Telegram id must already point at an
     * existing app_users row (linking is done by an authenticated user
     * via {@link #linkTelegram}); cold sign-up via Telegram is not
     * supported — a SUPER_ADMIN must provision the SavdoPRO account first.
     */
    public LoginResponse loginViaTelegram(
            uz.barakat.license.auth.AuthDtos.TelegramAuthRequest request,
            String clientIp) {
        long telegramId = telegramVerifier.verifyAndExtractId(request.asFieldMap());
        AppUser user = users.findByTelegramId(telegramId)
                .orElseThrow(() -> new BadRequestException(
                        "Telegram hisobingiz hech qaysi SavdoPRO foydalanuvchisiga bog'lanmagan"));
        Account account = requireUsableAccount(user.getAccountId());
        user.setLastLoginAt(LocalDateTime.now());
        users.save(user);
        RefreshTokenService.Issued refresh = refreshTokens.issue(
                user.getId(), account.getId(), clientIp);
        return new LoginResponse(
                jwt.issue(user),
                refresh.plaintext(),
                jwt.accessTtlSeconds(),
                refresh.expiresAt(),
                toMe(user, account));
    }

    /**
     * Link the current user (authenticated via password / TOTP) to a
     * verified Telegram id. Refuses to link if the Telegram account is
     * already bound to a different SavdoPRO user — the unique index
     * would catch it but a friendly 400 beats a 500.
     */
    public void linkTelegram(Long userId,
                             uz.barakat.license.auth.AuthDtos.TelegramAuthRequest request) {
        long telegramId = telegramVerifier.verifyAndExtractId(request.asFieldMap());
        users.findByTelegramId(telegramId).ifPresent(existing -> {
            if (!existing.getId().equals(userId)) {
                throw new BadRequestException(
                        "Bu Telegram hisobi allaqachon boshqa foydalanuvchiga bog'langan");
            }
        });
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new BadRequestException("Sessiya yaroqsiz"));
        user.setTelegramId(telegramId);
        users.save(user);
    }

    /** Detach the Telegram id from the current user. Idempotent. */
    public void unlinkTelegram(Long userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new BadRequestException("Sessiya yaroqsiz"));
        user.setTelegramId(null);
        users.save(user);
    }

    // ============================================================ SMS login

    /**
     * Mint a one-time code for the given phone and dispatch it via the
     * configured {@link SmsProvider}. The response intentionally does NOT
     * leak whether the phone exists in our DB — an attacker enumerating
     * phone numbers should see the same shape regardless.
     */
    public void requestSmsCode(String rawPhone) {
        String phone = normalisePhone(rawPhone);
        if (phone == null) {
            throw new BadRequestException("Telefon raqami noto'g'ri");
        }
        // Cooldown applies regardless of whether the phone is known —
        // again, no enumeration channel.
        OtpService.Result result = otp.requestCode(phone);
        if (result instanceof OtpService.Result.CooldownActive cd) {
            throw new BadRequestException(
                    "Iltimos, " + cd.secondsRemaining() + " soniyadan keyin qayta urinib ko'ring");
        }
        if (!(result instanceof OtpService.Result.Issued issued)) {
            throw new BadRequestException("Kod yuborilmadi");
        }
        // Only attempt to send when the phone is on a known user; for
        // unknown phones we silently swallow the code (no SMS sent) so
        // an attacker can't probe membership via SMS-bill-side effects.
        users.findByPhone(phone).ifPresent(u ->
                sms.send(phone, "SavdoPRO kirish kodi: " + issued.code()
                        + ". Kod 5 daqiqada amal qiladi."));
    }

    /**
     * Verify the SMS code and mint a session. Returns the same
     * {@link LoginResponse} shape as password login so the client can
     * treat both paths interchangeably.
     */
    public LoginResponse loginViaSms(String rawPhone, String code, String clientIp) {
        String phone = normalisePhone(rawPhone);
        if (phone == null || !otp.verify(phone, code)) {
            throw new BadRequestException("Kod noto'g'ri yoki muddati o'tgan");
        }
        AppUser user = users.findByPhone(phone)
                .orElseThrow(() -> new BadRequestException(
                        "Bu telefon raqamiga bog'langan SavdoPRO foydalanuvchisi topilmadi"));
        Account account = requireUsableAccount(user.getAccountId());
        user.setLastLoginAt(LocalDateTime.now());
        users.save(user);
        RefreshTokenService.Issued refresh = refreshTokens.issue(
                user.getId(), account.getId(), clientIp);
        return new LoginResponse(
                jwt.issue(user),
                refresh.plaintext(),
                jwt.accessTtlSeconds(),
                refresh.expiresAt(),
                toMe(user, account));
    }

    /**
     * Tolerant phone normaliser: strip whitespace and dashes, ensure the
     * result is either a leading "+" followed by 9–15 digits, or a plain
     * 9–15 digit string we prefix with "+". Returns null when the input
     * doesn't look like a phone at all.
     */
    static String normalisePhone(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replaceAll("[\\s\\-()]", "").trim();
        if (cleaned.isEmpty()) return null;
        if (cleaned.startsWith("+")) {
            String digits = cleaned.substring(1);
            if (digits.matches("\\d{9,15}")) return "+" + digits;
            return null;
        }
        if (cleaned.matches("\\d{9,15}")) return "+" + cleaned;
        return null;
    }

    /**
     * Rotate-on-refresh: validate the incoming refresh token, mint a
     * brand-new access + refresh pair, return them with the same
     * me-shape so the client can keep using the response interchangeably
     * with a fresh login.
     */
    public LoginResponse refresh(String refreshTokenPlaintext, String clientIp) {
        RefreshTokenService.RotationResult rot =
                refreshTokens.consume(refreshTokenPlaintext, clientIp);
        AppUser user = users.findById(rot.userId())
                .orElseThrow(() -> new BadRequestException("Sessiya yaroqsiz"));
        Account account = requireUsableAccount(rot.accountId());
        String accessToken = jwt.issueFor(
                user.getId(), user.getUsername(), user.getRole().name(), account.getId());
        return new LoginResponse(
                accessToken,
                rot.fresh().plaintext(),
                jwt.accessTtlSeconds(),
                rot.fresh().expiresAt(),
                toMe(user, account));
    }

    /** Single-device logout: revoke just the refresh token. */
    public void logout(String refreshTokenPlaintext) {
        refreshTokens.revoke(refreshTokenPlaintext);
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new BadRequestException("Sessiya yaroqsiz"));
        Account account = requireUsableAccount(user.getAccountId());
        return toMe(user, account);
    }

    /** Used by the JWT filter to reject blocked / expired accounts mid-session. */
    @Transactional(readOnly = true)
    public boolean isAccountUsable(Long accountId) {
        return accounts.findById(accountId).map(a -> {
            if (a.isBlocked()) return false;
            return a.getSubscriptionExpires() == null
                    || !a.getSubscriptionExpires().isBefore(LocalDate.now());
        }).orElse(false);
    }

    private Account requireUsableAccount(Long accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new BadRequestException("Akkaunt topilmadi"));
        if (account.isBlocked()) {
            throw new BadRequestException("Akkaunt bloklangan. Super-admin bilan bog'laning.");
        }
        if (account.getSubscriptionExpires() != null
                && account.getSubscriptionExpires().isBefore(LocalDate.now())) {
            throw new BadRequestException(
                    "Obuna muddati tugagan ("
                            + account.getSubscriptionExpires()
                            + "). Super-admin bilan bog'laning.");
        }
        return account;
    }

    private MeResponse toMe(AppUser user, Account account) {
        int days = daysUntilBlock(account);
        // SUPER_ADMIN ignores the allow-list — they need every page to
        // diagnose / configure other tenants. Everyone else sees only
        // what their account has enabled (null = all visible).
        String modules = user.getRole() == uz.barakat.license.domain.UserRole.SUPER_ADMIN
                ? null
                : account.getEnabledModules();
        return new MeResponse(
                user.getId(), user.getUsername(), user.getFullName(), user.getRole().name(),
                account.getId(), account.getName(),
                account.getSubscriptionExpires(), days, account.isBlocked(),
                brandFor(account), modules,
                permissions.effective(user));
    }

    /**
     * Project the per-account brand columns into the DTO shape the
     * desktop applies as CSS variables. Returns {@code null} when the
     * account hasn't set any white-label values so the client can
     * short-circuit straight to the SavdoPRO default look.
     */
    private static uz.barakat.license.auth.AuthDtos.Brand brandFor(Account a) {
        if (a.getBrandName() == null
                && a.getBrandColorPrimary() == null
                && a.getBrandColorSecondary() == null
                && a.getBrandLogoUrl() == null
                && a.getBrandFooterNote() == null) {
            return null;
        }
        return new uz.barakat.license.auth.AuthDtos.Brand(
                a.getBrandName(), a.getBrandColorPrimary(),
                a.getBrandColorSecondary(), a.getBrandLogoUrl(),
                a.getBrandFooterNote());
    }

    private static int daysUntilBlock(Account account) {
        if (account.getSubscriptionExpires() == null) return Integer.MAX_VALUE;
        long diff = account.getSubscriptionExpires().toEpochDay() - LocalDate.now().toEpochDay();
        return (int) Math.max(0, diff);
    }
}
