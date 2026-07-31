package com.okaynow.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateClientProfileRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        String addressLine,
        String city,
        @Size(max = 2) String state,
        String zip,
        Double lat,
        Double lng,
        @Size(max = 2000) String careNeeds
) {
}
