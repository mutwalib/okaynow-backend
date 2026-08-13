package com.okaynow.users.dto;

import com.okaynow.users.domain.Qualification;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.Set;

public record UpdateCaregiverProfileRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        Set<Qualification> qualifications,
        @DecimalMin("0.0") BigDecimal hourlyRateMin,
        @DecimalMin("0.0") BigDecimal hourlyRateMax,
        @Min(1) @Max(200) Integer serviceRadiusMiles,
        String homeAddressLine,
        String homeCity,
        String homeState,
        String homeZip,
        /** Optional; preferred path is address fields which the server geocodes. */
        Double homeLat,
        Double homeLng
) {
}
