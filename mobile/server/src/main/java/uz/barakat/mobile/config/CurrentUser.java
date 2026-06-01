package uz.barakat.mobile.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/** SecurityContext'dan joriy mijoz id sini olish uchun yordamchi. */
public final class CurrentUser {

    private CurrentUser() {}

    public static Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long customerId)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Avtorizatsiya talab qilinadi");
        }
        return customerId;
    }
}
