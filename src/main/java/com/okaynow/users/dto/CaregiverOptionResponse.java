package com.okaynow.users.dto;

import com.okaynow.users.domain.Qualification;

import java.util.Set;
import java.util.UUID;

public record CaregiverOptionResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        Set<Qualification> qualifications,
        Integer serviceRadiusMiles,
        Double homeLat,
        Double homeLng
) {
}
