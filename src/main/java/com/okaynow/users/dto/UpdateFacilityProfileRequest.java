package com.okaynow.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFacilityProfileRequest(
        @NotBlank String contactFirstName,
        @NotBlank String contactLastName,
        String phone,
        @NotBlank String addressLine,
        @NotBlank String city,
        @NotBlank @Size(min = 2, max = 2) String state,
        @NotBlank String zip,
        Double lat,
        Double lng,
        @Size(max = 2000) String notes
) {
}
