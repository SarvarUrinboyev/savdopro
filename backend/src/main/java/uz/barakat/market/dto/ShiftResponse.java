package uz.barakat.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import uz.barakat.market.domain.ShiftStatus;

/** API view of a shift, with the computed duration and that day's balance. */
public record ShiftResponse(
        Long id,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        String openedBy,
        ShiftStatus status,
        Long durationMinutes,
        BigDecimal startingCash,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal cashDifference) {
}
