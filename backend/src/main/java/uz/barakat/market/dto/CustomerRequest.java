package uz.barakat.market.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/** Create/update payload for a customer. */
public record CustomerRequest(
        @NotBlank(message = "Mijoz ismi kiritilishi shart") String name,
        String phone,
        String address,
        String note,
        LocalDate birthday) {
}
