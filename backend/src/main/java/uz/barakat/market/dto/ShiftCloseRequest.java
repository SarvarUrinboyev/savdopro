package uz.barakat.market.dto;

import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Optional cash count entered when closing a shift. When present it is
 * reconciled against the books' expected cash; a shortfall pings the owner.
 */
public record ShiftCloseRequest(
        @PositiveOrZero(message = "Sanab chiqilgan naqd manfiy bo'la olmaydi")
        BigDecimal countedCash) {
}
