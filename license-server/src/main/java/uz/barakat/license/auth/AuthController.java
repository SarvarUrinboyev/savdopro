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

    public AuthController(AuthService service, LoginRateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
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
