package uz.barakat.license.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.license.domain.AdminAuditEntry;
import uz.barakat.license.domain.AppUser;
import uz.barakat.license.repository.AdminAuditRepository;
import uz.barakat.license.repository.AppUserRepository;

/**
 * Append-only audit log. Every super-admin write (account / user
 * mutation, password reset, block) routes through {@link #record} so
 * the panel can show a full history of who did what, when, from which
 * IP. Reads are paginated and ordered newest-first.
 */
@Service
@Transactional
public class AuditService {

    /** Cap how many audit rows the panel can request in a single call. */
    private static final int MAX_PAGE_SIZE = 200;

    private final AdminAuditRepository audit;
    private final AppUserRepository users;
    private final HttpServletRequest request;

    public AuditService(AdminAuditRepository audit,
                        AppUserRepository users,
                        HttpServletRequest request) {
        this.audit = audit;
        this.users = users;
        this.request = request;
    }

    /**
     * Append one row. Pulls actor identity from the security context so
     * callers don't need to thread the user id through every service.
     * Best-effort — if the security context is empty (system-initiated
     * action like a scheduled cleanup) the row is still written under
     * the user id {@code -1} / name {@code "system"} rather than thrown
     * away, so the trail stays complete.
     */
    public void record(String action, String targetType, Long targetId,
                       String targetLabel, String detail) {
        AdminAuditEntry row = new AdminAuditEntry();
        Long actorId = currentUserId();
        row.setActorUserId(actorId == null ? -1L : actorId);
        row.setActorName(actorId == null ? "system" : resolveActorName(actorId));
        row.setAction(action);
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        row.setTargetLabel(targetLabel);
        row.setDetail(detail);
        row.setClientIp(clientIp());
        audit.save(row);
    }

    @Transactional(readOnly = true)
    public List<AdminAuditEntry> recent(int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Pageable p = PageRequest.of(Math.max(0, page), safeSize);
        Page<AdminAuditEntry> result = audit.findAllByOrderByCreatedAtDesc(p);
        return result.getContent();
    }

    // ------------------------------------------------------------ helpers

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof Long l) return l;
        try {
            return Long.parseLong(String.valueOf(principal));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Look up the username at audit-write time. Worst case (the actor
     * was deleted between this call and the row insert) returns "?"
     * so we never block a write on a missing lookup.
     */
    private String resolveActorName(Long userId) {
        return users.findById(userId).map(AppUser::getUsername).orElse("?");
    }

    private String clientIp() {
        if (request == null) return null;
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return (comma > 0 ? fwd.substring(0, comma) : fwd).trim();
        }
        return request.getRemoteAddr();
    }
}
