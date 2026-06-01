package uz.barakat.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import uz.barakat.mobile.domain.Address;

/** Manzil javob/so'rov modellari. */
public final class AddressDtos {

    private AddressDtos() {}

    public record AddressResponse(Long id, String label, String addressLine,
                                  Double lat, Double lng, String comment) {
        public static AddressResponse from(Address a) {
            return new AddressResponse(a.getId(), a.getLabel(), a.getAddressLine(),
                    a.getLat(), a.getLng(), a.getComment());
        }
    }

    public record CreateAddressRequest(
            @Size(max = 60) String label,
            @NotBlank @Size(max = 400) String addressLine,
            Double lat,
            Double lng,
            @Size(max = 300) String comment
    ) {}
}
