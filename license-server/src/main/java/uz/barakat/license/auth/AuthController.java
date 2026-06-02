package uz.barakat.license.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uz.barakat.license.auth.AuthDtos.LoginRequest;
import uz.barakat.license.auth.AuthDtos.LoginResponse;
import uz.barakat.license.auth.AuthDtos.MeResponse;
import uz.barakat.license.auth.AuthDtos.RefreshRequest;
import uz.barakat.license.auth.AuthDtos.ForgotPasswordRequest;
import uz.barakat.license.auth.AuthDtos.RegisterRequest;
import uz.barakat.license.auth.AuthDtos.ResetPasswordRequest;
import uz.barakat.license.auth.AuthDtos.SmsRequestRequest;
import uz.barakat.license.auth.AuthDtos.SmsVerifyRequest;
import uz.barakat.license.auth.AuthDtos.TelegramAuthRequest;
import uz.barakat.license.auth.AuthDtos.TotpSetupResponse;
import uz.barakat.license.auth.AuthDtos.TotpVerifyRequest;
import uz.barakat.license.exception.BadRequestException;

/**
 * Auth endpoints — login + current session.
 *
 * <p>Login is open (no JWT required), but {@link LoginRateLimiter} keeps
 * brute-force attempts in check by IP. {@code /me} requires a valid
 * {@code Authorization: Bearer ...} header that the JWT filter has
 * already validated.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService service;
    private final LoginRateLimiter rateLimiter;
    // Signup-screen feature flags (read once at startup). Phone verification is
    // only enforced when SMS is actually wired; social logins show only when
    // their provider is configured.
    private final boolean otpRequired;
    private final String telegramBot;
    private final GoogleOAuthVerifier googleVerifier;
    private final TelegramOAuthVerifier telegramVerifier;
    private final FacebookOAuthVerifier fbVerifier;
    private final XOAuthVerifier xVerifier;
    private final String signupTemplate;

    public AuthController(
            AuthService service, LoginRateLimiter rateLimiter,
            @org.springframework.beans.factory.annotation.Value(
                    "${savdopro.license.sms.signup-template:}") String signupTemplate,
            @org.springframework.beans.factory.annotation.Value(
                    "${savdopro.license.register.require-otp:false}") boolean otpRequired,
            @org.springframework.beans.factory.annotation.Value(
                    "${savdopro.license.telegram-oauth.bot-username:}") String telegramBot,
            GoogleOAuthVerifier googleVerifier,
            TelegramOAuthVerifier telegramVerifier,
            FacebookOAuthVerifier fbVerifier,
            XOAuthVerifier xVerifier) {
        this.service = service;
        this.rateLimiter = rateLimiter;
        this.signupTemplate = signupTemplate;
        this.otpRequired = otpRequired;
        this.telegramBot = telegramBot;
        this.googleVerifier = googleVerifier;
        this.telegramVerifier = telegramVerifier;
        this.fbVerifier = fbVerifier;
        this.xVerifier = xVerifier;
    }

    /** What the signup screen renders: enforced verification + enabled logins. */
    @GetMapping("/signup/config")
    public AuthDtos.SignupConfigResponse signupConfig() {
        Long tgBotId = telegramVerifier.botId();
        return new AuthDtos.SignupConfigResponse(
                otpRequired,
                telegramVerifier.isConfigured(),
                (telegramBot == null || telegramBot.isBlank()) ? null : telegramBot,
                tgBotId == null ? null : String.valueOf(tgBotId),
                googleVerifier.isConfigured(), fbVerifier.isConfigured(), xVerifier.isConfigured(),
                googleVerifier.clientIdOrNull(),
                fbVerifier.appIdOrNull(), xVerifier.clientIdOrNull(), xVerifier.redirectUriOrNull());
    }

    /** Signup step 1: SMS a verification code to the entered phone. */
    @PostMapping("/signup/request-otp")
    public void signupRequestOtp(@Valid @RequestBody AuthDtos.SignupOtpRequest request,
                                 HttpServletRequest http) {
        String ip = clientIp(http);
        if (!rateLimiter.allow(ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Juda ko'p urinish. Birozdan keyin qayta urinib ko'ring.");
        }
        rateLimiter.recordFailure(ip); // throttle code requests per IP
        service.requestSignupOtp(request.phone(), signupTemplate);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest http) {
        String ip = clientIp(http);
        if (!rateLimiter.allow(ip)) {
            log.warn("Login rejected — rate limit hit: ip={} username={}",
                    ip, request == null ? null : request.username());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Juda ko'p urinish. Birozdan keyin qayta urinib ko'ring.");
        }
        try {
            LoginResponse response = service.login(request, ip);
            rateLimiter.recordSuccess(ip);
            return response;
        } catch (RuntimeException ex) {
            rateLimiter.recordFailure(ip);
            throw ex;
        }
    }

    /** Public self-service signup → trial account + owner, returns a session. */
    @PostMapping("/register")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request,
                                  HttpServletRequest http) {
        String ip = clientIp(http);
        if (!rateLimiter.allow(ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Juda ko'p urinish. Birozdan keyin qayta urinib ko'ring.");
        }
        // Count each signup toward the per-IP limit so a script can't mass-create
        // trial accounts.
        rateLimiter.recordFailure(ip);
        // Phone verification: when enforced, the SMS code must check out before
        // an account is created — this is what stops random/fake phone signups.
        if (otpRequired) {
            service.verifySignupOtp(request.phone(), request.code());
        }
        return service.register(request, ip);
    }

    /**
     * Social login: mint a session from a Google account chooser. The browser
     * sends the Google access token; we verify it (audience + profile) against
     * Google, then log in an existing Google user or cold-create a trial
     * account. Public endpoint — the verified Google token is the credential.
     * Inert until {@code savdopro.license.oauth.google.client-id} is set.
     */
    @PostMapping("/social/google")
    public LoginResponse googleLogin(@Valid @RequestBody AuthDtos.GoogleAuthRequest request,
                                     HttpServletRequest http) {
        String ip = clientIp(http);
        if (!rateLimiter.allow(ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Juda ko'p urinish. Birozdan keyin qayta urinib ko'ring.");
        }
        rateLimiter.recordFailure(ip); // bound social signups per IP too
        GoogleOAuthVerifier.GoogleUser u = googleVerifier.verify(request.accessToken());
        return service.loginViaGoogle(u.email(), u.sub(), u.name(), ip);
    }

    /** Social login: Facebook. Inert until the FB app id/secret are configured. */
    @PostMapping("/social/facebook")
    public LoginResponse facebookLogin(@Valid @RequestBody AuthDtos.FacebookAuthRequest request,
                                       HttpServletRequest http) {
        String ip = clientIp(http);
        if (!rateLimiter.allow(ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Juda ko'p urinish. Birozdan keyin qayta urinib ko'ring.");
        }
        rateLimiter.recordFailure(ip);
        FacebookOAuthVerifier.FacebookUser u = fbVerifier.verify(request.accessToken());
        return service.loginViaFacebook(u.id(), u.email(), u.name(), ip);
    }

    /** Social login: X (Twitter) OAuth2 code+PKCE. Inert until X creds configured. */
    @PostMapping("/social/x")
    public LoginResponse xLogin(@Valid @RequestBody AuthDtos.XAuthRequest request,
                                HttpServletRequest http) {
        String ip = clientIp(http);
        if (!rateLimiter.allow(ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Juda ko'p urinish. Birozdan keyin qayta urinib ko'ring.");
        }
        rateLimiter.recordFailure(ip);
        XOAuthVerifier.XUser u = xVerifier.exchange(request.code(), request.codeVerifier());
        return service.loginViaX(u.id(), u.name(), ip);
    }

    /** Forgot-password step 1: SMS a reset code (only to a registered phone). */
    @PostMapping("/forgot-password")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                               HttpServletRequest http) {
        String ip = clientIp(http);
        if (!rateLimiter.allow(ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Juda ko'p urinish. Birozdan keyin qayta urinib ko'ring.");
        }
        rateLimiter.recordFailure(ip); // throttle SMS-code requests per IP
        service.requestPasswordResetCode(request.phone());
    }

    /** Forgot-password step 2: verify the code and set the new password. */
    @PostMapping("/reset-password")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                              HttpServletRequest http) {
        String ip = clientIp(http);
        if (!rateLimiter.allow(ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Juda ko'p urinish. Birozdan keyin qayta urinib ko'ring.");
        }
        try {
            service.resetPassword(request.phone(), request.code(), request.newPassword());
        } catch (RuntimeException ex) {
            rateLimiter.recordFailure(ip); // count failed code-guess attempts
            throw ex;
        }
    }

    /**
     * Rotate-on-refresh: exchange a valid refresh token for a new
     * access + refresh pair. Public endpoint (no JWT required) but the
     * refresh token itself acts as the credential.
     */
    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request,
                                 HttpServletRequest http) {
        return service.refresh(request.refreshToken(), clientIp(http));
    }

    /**
     * Single-device logout: invalidates the supplied refresh token. The
     * caller is also expected to discard their access JWT client-side
     * — we can't revoke it here because it's stateless.
     */
    @PostMapping("/logout")
    public void logout(@RequestBody(required = false) RefreshRequest request) {
        if (request != null) {
            service.logout(request.refreshToken());
        }
    }

    /** Returns the current session, or 401 if the token is missing / invalid. */
    @GetMapping("/me")
    public MeResponse me(HttpServletRequest request) {
        Object uid = request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        if (uid == null) {
            throw new BadRequestException("Sessiya yo'q");
        }
        return service.me((Long) uid);
    }

    // ============================================================ TOTP

    /**
     * Generate a fresh TOTP secret + otpauth URI for the current user.
     * Calling this resets any previous unconfirmed setup — useful when
     * the user lost their authenticator app mid-flow.
     */
    @PostMapping("/totp/setup")
    public TotpSetupResponse totpSetup(HttpServletRequest request) {
        return service.setupTotp(requireUserId(request));
    }

    /** Confirm the first authenticator code and flip 2FA on. */
    @PostMapping("/totp/confirm")
    public void totpConfirm(HttpServletRequest request,
                            @Valid @RequestBody TotpVerifyRequest body) {
        service.confirmTotp(requireUserId(request), body.code());
    }

    /** Turn 2FA off and wipe the secret. */
    @PostMapping("/totp/disable")
    public void totpDisable(HttpServletRequest request) {
        service.disableTotp(requireUserId(request));
    }

    // ============================================================ Telegram OAuth

    /**
     * Mint a session from a verified Telegram Login Widget payload.
     * Public endpoint (no JWT required) — the Telegram HMAC itself
     * is the credential. The Telegram id must already be linked to an
     * existing user (see {@code /telegram/link}); cold sign-up via
     * Telegram is intentionally not supported.
     */
    @PostMapping("/telegram")
    public LoginResponse telegramLogin(@Valid @RequestBody TelegramAuthRequest request,
                                       HttpServletRequest http) {
        return service.loginOrRegisterViaTelegram(request, clientIp(http));
    }

    /**
     * Attach the current session's user to a verified Telegram id. Used
     * once, from the "Telegram'ni ulash" button inside the settings page.
     */
    @PostMapping("/telegram/link")
    public void telegramLink(HttpServletRequest request,
                             @Valid @RequestBody TelegramAuthRequest body) {
        service.linkTelegram(requireUserId(request), body);
    }

    /** Detach the Telegram id from the current user. Idempotent. */
    @PostMapping("/telegram/unlink")
    public void telegramUnlink(HttpServletRequest request) {
        service.unlinkTelegram(requireUserId(request));
    }

    // ============================================================ SMS login

    /**
     * Request an SMS one-time code for the given phone. Public endpoint —
     * the response is intentionally opaque (no DB-membership signal).
     */
    @PostMapping("/sms/request")
    public void smsRequest(@Valid @RequestBody SmsRequestRequest body) {
        service.requestSmsCode(body.phone());
    }

    /** Exchange a valid SMS code for a session. */
    @PostMapping("/sms/verify")
    public LoginResponse smsVerify(@Valid @RequestBody SmsVerifyRequest body,
                                   HttpServletRequest http) {
        return service.loginViaSms(body.phone(), body.code(), clientIp(http));
    }

    private static Long requireUserId(HttpServletRequest request) {
        Object uid = request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        if (uid == null) throw new BadRequestException("Sessiya yo'q");
        return (Long) uid;
    }

    /**
     * Resolves the caller's IP, honouring {@code X-Forwarded-For} so a
     * reverse proxy (Caddy / nginx in front of the VPS) doesn't collapse
     * every client to the loopback address. Trust only the first hop in
     * the header — anything else is forgeable.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
