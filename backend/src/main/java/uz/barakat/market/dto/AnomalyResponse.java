package uz.barakat.market.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A persisted anomaly alert, exposed by {@code GET /api/ai/anomalies/history}.
 *
 * <p>Deliberately a superset of the four fields the dashboard
 * {@code AnomalyBanner.jsx} reads ({@code severity, code, message, at}), so the
 * same component renders both the live and persisted feeds unchanged. {@code at}
 * mirrors {@code createdAt}; {@code occurredOn} is the business day the anomaly
 * is about.
 */
public record AnomalyResponse(
        Long id,
        String severity,
        String code,
        String message,
        LocalDateTime at,
        LocalDate occurredOn,
        boolean acknowledged,
        String acknowledgedBy,
        LocalDateTime acknowledgedAt,
        String detailJson) {
}
