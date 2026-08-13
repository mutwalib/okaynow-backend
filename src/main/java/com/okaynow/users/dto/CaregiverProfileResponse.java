package com.okaynow.users.dto;

import com.okaynow.users.domain.Qualification;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record CaregiverProfileResponse(
        UUID id,
        UUID userId,
        String firstName,
        String lastName,
        Set<Qualification> qualifications,
        BigDecimal hourlyRateMin,
        BigDecimal hourlyRateMax,
        Integer serviceRadiusMiles,
        String homeAddressLine,
        String homeCity,
        String homeState,
        String homeZip,
        Double homeLat,
        Double homeLng,
        String profilePhotoUrl,
        BigDecimal ratingAvg,
        Integer ratingCount
) {
}
