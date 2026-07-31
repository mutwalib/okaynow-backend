package com.okaynow.users.dto;

import java.util.UUID;

public record FacilityProfileResponse(
        UUID id,
        UUID userId,
        String email,
        String phone,
        String facilityName,
        String contactFirstName,
        String contactLastName,
        String addressLine,
        String city,
        String state,
        String zip,
        Double lat,
        Double lng,
        String notes
) {
}
