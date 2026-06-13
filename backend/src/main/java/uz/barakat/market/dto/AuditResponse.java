package uz.barakat.market.dto;

import java.time.LocalDateTime;

/** One row of the local data-mutation audit trail. */
public record AuditResponse(
        Long id, String actor, String method, String path, int status,
        String clientIp, LocalDateTime createdAt) {
}
