package uz.barakat.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Auth bilan bog'liq so'rov/javob modellari. */
public final class AuthDtos {

    private AuthDtos() {}

    public record RequestOtpRequest(
            @NotBlank @Pattern(regexp = "\\+?[0-9]{9,15}", message = "Telefon raqami noto'g'ri")
            String phone
    ) {}

    public record RequestOtpResponse(String phone, int expiresInSeconds, String devCode) {}

    public record VerifyOtpRequest(
            @NotBlank String phone,
            @NotBlank @Size(min = 4, max = 6) String code
    ) {}

    public record AuthResponse(String token, CustomerResponse customer) {}

    public record CustomerResponse(Long id, String phone, String name, String email) {}

    public record UpdateProfileRequest(
            @Size(max = 120) String name,
            @Size(max = 160) String email
    ) {}
}
