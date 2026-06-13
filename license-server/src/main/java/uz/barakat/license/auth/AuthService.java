package uz.barakat.license.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.license.auth.AuthDtos.LoginRequest;
import uz.barakat.license.auth.AuthDtos.RegisterRequest;
import uz.barakat.license.domain.SubscriptionPlan;
import uz.barakat.license.domain.UserRole;
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
    private final SuspiciousLoginAlerter alerter;
    // Cost 12 — see AdminBootstrap; old hashes (cost 10) still verify
    // because BCrypt stores the cost inside the hash, so existing users
    // keep logging in until their next password reset re-hashes at 12.
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public AuthService(AppUserRepository users, AccountRepository accounts,
                       JwtService jwt, RefreshTokenService refreshTokens,
                       TotpService totp, TelegramOAuthVerifier telegramVerifier,
                       OtpService otp, SmsProvider sms,
                       PermissionService permissions, SuspiciousLoginAlerter alerter) {
        this.users = users;
        this.accounts = accounts;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.totp = totp;
        this.telegramVerifier = telegramVerifier;
        this.otp = otp;
        this.sms = sms;
        this.permissions = permissions;
        this.alerter = alerter;
    }

    /** Trial length granted to a brand-new self-service signup. */
    public static final int TRIAL_DAYS = 3;

    /**
     * Self-service merchant signup: creates a trial account + its owner user
     * and returns a session (auto-login), exactly like {@link #login}. Public
     * endpoint — abuse is bounded by the per-IP rate limit on the controller
     * and (next step) email/SMS verification.
     */
    @Transactional
    public LoginResponse register(RegisterRequest req, String clientIp) {
        String username = req.username().trim().toLowerCase();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new BadRequestException("Bu login band: " + username);
        }
        Account account = new Account();
        account.setName(req.businessName().trim());
        account.setContactPhone(
                req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        account.setSubscriptionExpires(LocalDate.now().plusDays(TRIAL_DAYS));
        account.setBlocked(false);
        account.setPlan(SubscriptionPlan.TRIAL);
        Account saved = accounts.save(account);

        AppUser owner = new AppUser();
        owner.setUsername(username);
        owner.setPasswordHash(encoder.encode(req.password()));
        owner.setFullName(req.fullName().trim());
        owner.setRole(UserRole.ACCOUNT_OWNER);
        owner.setAccountId(saved.getId());
        try {
            users.save(owner);
        } catch (DataIntegrityViolationException ex) {
            // Lost the race for this username — friendly 400, not a 500.
            throw new BadRequestException("Bu login band: " + username);
        }

        // Tell the super-admin a new merchant just signed up (best-effort).
        alerter.notifyNewSignup(saved.getName(), saved.getContactPhone(),
                owner.getUsername(), TRIAL_DAYS);

        RefreshTokenService.Issued refresh =
                refreshTokens.issue(owner.getId(), saved.getId(), clientIp);
        return new LoginResponse(jwt.issue(owner), refresh.plaintext(),
                jwt.accessTtlSeconds(), refresh.expiresAt(), toMe(owner, saved));
    }

    /**
     * Sign in (or cold sign-up) via a verified Google identity. Google
     * accounts are keyed by their email used as the username — an email
     * always contains '@', which normal hand-picked logins never do, so the
     * two namespaces can't collide and one Google account maps to exactly
     * one SavdoPRO account. First-time Google users get a fresh 3-day trial
     * account + owner (no usable password — they always come back via
     * Google). Returns a session exactly like {@link #login}.
     *
     * <p>The Google token is verified upstream in the controller
     * ({@link GoogleOAuthVerifier}); this method trusts the email it is given.
     */
    @Transactional
    public LoginResponse loginViaGoogle(String email, String googleSub,
                                        String displayName, String clientIp) {
        String normEmail = email == null ? "" : email.trim().toLowerCase();
        if (normEmail.isBlank() || normEmail.indexOf('@') <= 0) {
            throw new BadRequestException("Google hisobida email topilmadi");
        }
        // Returning Google user → log straight in.
        var existing = users.findByUsernameIgnoreCase(normEmail);
        if (existing.isPresent()) {
            AppUser user = existing.get();
            Account account = requireUsableAccount(user.getAccountId());
            user.setLastLoginAt(LocalDateTime.now());
            users.save(user);
            RefreshTokenService.Issued refresh =
                    refreshTokens.issue(user.getId(), account.getId(), clientIp);
            return new LoginResponse(jwt.issue(user), refresh.plaintext(),
                    jwt.accessTtlSeconds(), refresh.expiresAt(), toMe(user, account));
        }
        // First time → create a trial account + its owner (auto-login).
        String shopName = (displayName != null && !displayName.isBlank())
                ? displayName.trim()
                : normEmail.substring(0, normEmail.indexOf('@'));
        Account account = new Account();
        account.setName(shopName);
        account.setContactPhone(null);
        account.setSubscriptionExpires(LocalDate.now().plusDays(TRIAL_DAYS));
        account.setBlocked(false);
        account.setPlan(SubscriptionPlan.TRIAL);
        Account saved = accounts.save(account);

        AppUser owner = new AppUser();
        owner.setUsername(normEmail);
        // Unusable random password — Google is the only way into this account
        // until the owner sets one via password reset.
        owner.setPasswordHash(encoder.encode(java.util.UUID.randomUUID().toString()));
        owner.setFullName((displayName != null && !displayName.isBlank())
                ? displayName.trim() : normEmail);
        owner.setRole(UserRole.ACCOUNT_OWNER);
        owner.setAccountId(saved.getId());
        try {
            users.save(owner);
        } catch (DataIntegrityViolationException ex) {
            // Raced with a parallel Google login for the same email — the first
            // one won; just log into whatever now exists rather than 500.
            AppUser raced = users.findByUsernameIgnoreCase(normEmail)
                    .orElseThrow(() -> new BadRequestException("Google orqali kirib bo'lmadi"));
            Account racedAcc = requireUsableAccount(raced.getAccountId());
            RefreshTokenService.Issued rr =
                    refreshTokens.issue(raced.getId(), racedAcc.getId(), clientIp);
            return new LoginResponse(jwt.issue(raced), rr.plaintext(),
                    jwt.accessTtlSeconds(), rr.expiresAt(), toMe(raced, racedAcc));
        }

        // Tell the super-admin a new merchant just signed up (best-effort).
        alerter.notifyNewSignup(saved.getName(), null, owner.getUsername(), TRIAL_DAYS);

        RefreshTokenService.Issued refresh =
                refreshTokens.issue(owner.getId(), saved.getId(), clientIp);
        return new LoginResponse(jwt.issue(owner), refresh.plaintext(),
                jwt.accessTtlSeconds(), refresh.expiresAt(), toMe(owner, saved));
    }

    /**
     * Facebook login: verified upstream by {@link FacebookOAuthVerifier}. Keyed
     * by the verified email when Facebook returns one (App Review granted the
     * email permission), else by the synthetic {@code fb_<id>} username.
     */
    @Transactional
    public LoginResponse loginViaFacebook(String id, String email, String name, String clientIp) {
        String key = (email != null && !email.isBlank())
                ? email.trim().toLowerCase() : "fb_" + id;
        return socialSession(key, name, clientIp);
    }

    /**
     * X (Twitter) login: verified upstream by {@link XOAuthVerifier}. X does not
     * expose email, so accounts are keyed by the synthetic {@code x_<id>}.
     */
    @Transactional
    public LoginResponse loginViaX(String id, String name, String clientIp) {
        return socialSession("x_" + id, name, clientIp);
    }

    /**
     * Shared cold-signup for the email-less social providers (Facebook / X):
     * log in the user owning {@code usernameKey}, or create a fresh 3-day trial
     * account + owner. The key is synthetic ({@code fb_<id>}/{@code x_<id>}) or a
     * verified email — none of which collide with hand-picked logins.
     */
    private LoginResponse socialSession(String usernameKey, String displayName, String clientIp) {
        var existing = users.findByUsernameIgnoreCase(usernameKey);
        if (existing.isPresent()) {
            AppUser user = existing.get();
            Account account = requireUsableAccount(user.getAccountId());
            user.setLastLoginAt(LocalDateTime.now());
            users.save(user);
            RefreshTokenService.Issued refresh =
                    refreshTokens.issue(user.getId(), account.getId(), clientIp);
            return new LoginResponse(jwt.issue(user), refresh.plaintext(),
                    jwt.accessTtlSeconds(), refresh.expiresAt(), toMe(user, account));
        }
        String name = (displayName == null || displayName.isBlank())
                ? usernameKey : displayName.trim();
        Account account = new Account();
        account.setName(name);
        account.setContactPhone(null);
        account.setSubscriptionExpires(LocalDate.now().plusDays(TRIAL_DAYS));
        account.setBlocked(false);
        account.setPlan(SubscriptionPlan.TRIAL);
        Account saved = accounts.save(account);

        AppUser owner = new AppUser();
        owner.setUsername(usernameKey);
        owner.setPasswordHash(encoder.encode(java.util.UUID.randomUUID().toString()));
        owner.setFullName(name);
        owner.setRole(UserRole.ACCOUNT_OWNER);
        owner.setAccountId(saved.getId());
        try {
            users.save(owner);
        } catch (DataIntegrityViolationException ex) {
            AppUser raced = users.findByUsernameIgnoreCase(usernameKey)
                    .orElseThrow(() -> new BadRequestException("Ijtimoiy tarmoq orqali kirib bo'lmadi"));
            Account racedAcc = requireUsableAccount(raced.getAccountId());
            RefreshTokenService.Issued rr =
                    refreshTokens.issue(raced.getId(), racedAcc.getId(), clientIp);
            return new LoginResponse(jwt.issue(raced), rr.plaintext(),
                    jwt.accessTtlSeconds(), rr.expiresAt(), toMe(raced, racedAcc));
        }
        alerter.notifyNewSignup(saved.getName(), null, owner.getUsername(), TRIAL_DAYS);
        RefreshTokenService.Issued refresh =
                refreshTokens.issue(owner.getId(), saved.getId(), clientIp);
        return new LoginResponse(jwt.issue(owner), refresh.plaintext(),
                jwt.accessTtlSeconds(), refresh.expiresAt(), toMe(owner, saved));
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
            // Accept the authenticator code OR a one-time backup code. The
            // backup code is consumed in-memory here and persisted by the
            // users.save below — so a single code can never be reused.
            if (!totp.verify(user.getTotpSecret(), code) && !consumeBackupCode(user, code)) {
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

    /**
     * Verify the first code from the authenticator, enable 2FA, and issue a
     * fresh set of one-time backup codes. The plaintext codes are returned
     * ONCE (only their hashes are stored) for the user to save somewhere safe.
     */
    public List<String> confirmTotp(Long userId, String code) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new BadRequestException("Sessiya yaroqsiz"));
        if (user.getTotpSecret() == null) {
            throw new BadRequestException("Avval TOTP sozlamasini boshlang");
        }
        if (!totp.verify(user.getTotpSecret(), code)) {
            throw new BadRequestException("Kod noto'g'ri — qaytadan urinib ko'ring");
        }
        user.setTotpEnabled(true);
        List<String> codes = generateBackupCodes();
        user.setTotpBackupCodes(hashJoin(codes));
        users.save(user);
        return codes;
    }

    /** Re-issues backup codes (invalidates the old set). 2FA must be enabled. */
    public List<String> regenerateBackupCodes(Long userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new BadRequestException("Sessiya yaroqsiz"));
        if (!user.isTotpEnabled()) {
            throw new BadRequestException("Avval 2FA ni yoqing");
        }
        List<String> codes = generateBackupCodes();
        user.setTotpBackupCodes(hashJoin(codes));
        users.save(user);
        return codes;
    }

    // -------------------------------------------------------- backup codes

    private static final SecureRandom RNG = new SecureRandom();
    private static final int BACKUP_CODE_COUNT = 8;
    // Unambiguous alphabet (no 0/O/1/I) so hand-typed codes don't trip on look-alikes.
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < 10; c++) {
                if (c == 5) {
                    sb.append('-');
                }
                sb.append(CODE_ALPHABET.charAt(RNG.nextInt(CODE_ALPHABET.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    /** Consumes one matching backup code (mutates the user in place). */
    private boolean consumeBackupCode(AppUser user, String input) {
        String stored = user.getTotpBackupCodes();
        if (stored == null || stored.isBlank()) {
            return false;
        }
        String target = sha256(normalizeCode(input));
        List<String> remaining = new ArrayList<>();
        boolean matched = false;
        for (String h : stored.split("\n")) {
            if (h.isBlank()) {
                continue;
            }
            if (!matched && h.equals(target)) {
                matched = true;   // consume exactly one
            } else {
                remaining.add(h);
            }
        }
        if (matched) {
            user.setTotpBackupCodes(String.join("\n", remaining));
        }
        return matched;
    }

    private static String hashJoin(List<String> codes) {
        List<String> hashes = new ArrayList<>();
        for (String c : codes) {
            hashes.add(sha256(normalizeCode(c)));
        }
        return String.join("\n", hashes);
    }

    private static String normalizeCode(String c) {
        return c == null ? "" : c.trim().toUpperCase().replace("-", "");
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
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
     * Telegram login for the PUBLIC signup screen: like
     * {@link #loginViaTelegram} but cold-signs-up a brand-new Telegram user
     * instead of rejecting them. Returning users (telegram_id already known)
     * log straight in; first-timers get a fresh 3-day trial account + owner,
     * keyed by the unique {@code telegram_id} column (Telegram gives no email).
     * The username is the synthetic {@code tg_<id>} so it can never collide
     * with a hand-picked login or a Google email-as-username.
     */
    @Transactional
    public LoginResponse loginOrRegisterViaTelegram(
            uz.barakat.license.auth.AuthDtos.TelegramAuthRequest request,
            String clientIp) {
        long telegramId = telegramVerifier.verifyAndExtractId(request.asFieldMap());
        var existing = users.findByTelegramId(telegramId);
        if (existing.isPresent()) {
            AppUser user = existing.get();
            Account account = requireUsableAccount(user.getAccountId());
            user.setLastLoginAt(LocalDateTime.now());
            users.save(user);
            RefreshTokenService.Issued refresh =
                    refreshTokens.issue(user.getId(), account.getId(), clientIp);
            return new LoginResponse(jwt.issue(user), refresh.plaintext(),
                    jwt.accessTtlSeconds(), refresh.expiresAt(), toMe(user, account));
        }
        // First time → trial account + owner (auto-login).
        String fn = request.firstName() == null ? "" : request.firstName().trim();
        String ln = request.lastName() == null ? "" : request.lastName().trim();
        String fullName = (fn + " " + ln).trim();
        if (fullName.isBlank()) {
            fullName = (request.username() != null && !request.username().isBlank())
                    ? request.username().trim() : "Telegram foydalanuvchi";
        }
        Account account = new Account();
        account.setName(fullName);
        account.setContactPhone(null);
        account.setSubscriptionExpires(LocalDate.now().plusDays(TRIAL_DAYS));
        account.setBlocked(false);
        account.setPlan(SubscriptionPlan.TRIAL);
        Account saved = accounts.save(account);

        AppUser owner = new AppUser();
        owner.setUsername("tg_" + telegramId);
        owner.setPasswordHash(encoder.encode(java.util.UUID.randomUUID().toString()));
        owner.setFullName(fullName);
        owner.setRole(UserRole.ACCOUNT_OWNER);
        owner.setAccountId(saved.getId());
        owner.setTelegramId(telegramId);
        try {
            users.save(owner);
        } catch (DataIntegrityViolationException ex) {
            // Raced with a parallel Telegram login → use whatever now exists.
            AppUser raced = users.findByTelegramId(telegramId)
                    .orElseThrow(() -> new BadRequestException("Telegram orqali kirib bo'lmadi"));
            Account racedAcc = requireUsableAccount(raced.getAccountId());
            RefreshTokenService.Issued rr =
                    refreshTokens.issue(raced.getId(), racedAcc.getId(), clientIp);
            return new LoginResponse(jwt.issue(raced), rr.plaintext(),
                    jwt.accessTtlSeconds(), rr.expiresAt(), toMe(raced, racedAcc));
        }
        alerter.notifyNewSignup(saved.getName(), null, owner.getUsername(), TRIAL_DAYS);
        RefreshTokenService.Issued refresh =
                refreshTokens.issue(owner.getId(), saved.getId(), clientIp);
        return new LoginResponse(jwt.issue(owner), refresh.plaintext(),
                jwt.accessTtlSeconds(), refresh.expiresAt(), toMe(owner, saved));
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

    /** Forgot-password: send a reset code by SMS — only to a registered phone. */
    public void requestPasswordResetCode(String rawPhone) {
        String phone = normalisePhone(rawPhone);
        if (phone == null) {
            throw new BadRequestException("Telefon raqami noto'g'ri");
        }
        OtpService.Result result = otp.requestCode(phone);
        if (result instanceof OtpService.Result.CooldownActive cd) {
            throw new BadRequestException(
                    "Iltimos, " + cd.secondsRemaining() + " soniyadan keyin qayta urinib ko'ring");
        }
        if (result instanceof OtpService.Result.Issued issued) {
            users.findByPhone(phone).ifPresent(u ->
                    sms.send(phone, "SavdoPRO parol tiklash kodi: " + issued.code()
                            + ". Kod 5 daqiqada amal qiladi."));
        }
    }

    /**
     * Signup step 1: SMS a verification code to the phone the new merchant
     * entered. Unlike the reset flow this does NOT require a pre-existing
     * user (the account doesn't exist yet). Best-effort: a CooldownActive
     * surfaces as a friendly error; a bad phone is ignored silently.
     */
    public void requestSignupOtp(String rawPhone, String template) {
        String phone = normalisePhone(rawPhone);
        if (phone == null) {
            throw new BadRequestException("Telefon raqami noto'g'ri");
        }
        OtpService.Result result = otp.requestCode(phone);
        if (result instanceof OtpService.Result.CooldownActive cd) {
            throw new BadRequestException(
                    "Iltimos, " + cd.secondsRemaining() + " soniyadan keyin qayta urinib ko'ring");
        }
        if (result instanceof OtpService.Result.Issued issued) {
            // The SMS text MUST match the operator's Eskiz-approved template
            // exactly (Eskiz rejects unknown texts), so it's configurable:
            // {code} is substituted with the one-time code.
            String body = (template == null || template.isBlank()
                    ? "SavdoPRO tasdiqlash kodi: {code}. Kod 5 daqiqa amal qiladi."
                    : template).replace("{code}", issued.code());
            sms.send(phone, body);
        }
    }

    /** Signup step 2: throws when the SMS code is missing, wrong or expired. */
    public void verifySignupOtp(String rawPhone, String code) {
        String phone = normalisePhone(rawPhone);
        if (phone == null || code == null || code.isBlank() || !otp.verify(phone, code)) {
            throw new BadRequestException("Tasdiqlash kodi noto'g'ri yoki muddati o'tgan");
        }
    }

    /** Forgot-password: verify the code, set the new password, kill old sessions. */
    @Transactional
    public void resetPassword(String rawPhone, String code, String newPassword) {
        String phone = normalisePhone(rawPhone);
        if (phone == null || !otp.verify(phone, code)) {
            throw new BadRequestException("Kod noto'g'ri yoki muddati o'tgan");
        }
        AppUser user = users.findByPhone(phone)
                .orElseThrow(() -> new BadRequestException(
                        "Bu telefon raqamiga bog'langan foydalanuvchi topilmadi"));
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
        // A reset invalidates every existing session so a leaked refresh token
        // cannot outlive the password change.
        refreshTokens.revokeAllForUser(user.getId());
    }

    /** Read-only subscription snapshot for the billing page (current account). */
    @Transactional(readOnly = true)
    public AuthDtos.SubscriptionStatusResponse subscriptionStatus(Long userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new BadRequestException("Sessiya yaroqsiz"));
        Account account = accounts.findById(user.getAccountId())
                .orElseThrow(() -> new BadRequestException("Akkaunt topilmadi"));
        SubscriptionPlan plan = account.getPlan();
        LocalDate expires = account.getSubscriptionExpires();
        boolean expired = expires != null && expires.isBefore(LocalDate.now());
        return new AuthDtos.SubscriptionStatusResponse(
                plan.name(),
                plan.monthlyPriceUzs(),
                expires,
                Math.max(daysUntilBlock(account), 0),
                expired,
                account.isBlocked(),
                plan.maxUsers(),
                users.countByAccountId(user.getAccountId()),
                plan.maxShops());
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
        // Re-issue a full access token (incl. the effective perms claim) for
        // the refreshed session — same shape as a fresh login.
        String accessToken = jwt.issue(user);
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

    /**
     * True only when the account is manually BLOCKED by a super-admin. An
     * expired subscription is deliberately NOT treated as blocked here — the
     * merchant must still reach /me + /billing to see status and renew
     * (read-only is enforced on the shop backend, not the license API).
     */
    @Transactional(readOnly = true)
    public boolean isAccountBlocked(Long accountId) {
        return accounts.findById(accountId).map(Account::isBlocked).orElse(false);
    }

    private Account requireUsableAccount(Long accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new BadRequestException("Akkaunt topilmadi"));
        if (account.isBlocked()) {
            throw new BadRequestException("Akkaunt bloklangan. Super-admin bilan bog'laning.");
        }
        // An expired subscription is NOT a hard stop: the user logs in
        // read-only and is steered to billing to renew. The JWT carries a
        // subExp claim and the shop backend blocks writes while it is in the
        // past — see JwtAuthFilter. Only a manual super-admin block stops login.
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
        boolean mfaSetupRequired = account.isRequireMfa() && !user.isTotpEnabled();
        return new MeResponse(
                user.getId(), user.getUsername(), user.getFullName(), user.getRole().name(),
                account.getId(), account.getName(),
                account.getSubscriptionExpires(), days, account.isBlocked(),
                brandFor(account), modules,
                permissions.effective(user), mfaSetupRequired);
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
