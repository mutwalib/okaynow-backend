package com.okaynow.roster.dto;

import com.okaynow.users.domain.Qualification;

import java.util.List;
import java.util.UUID;

/** Slim caregiver profile for agency recruiting / email lookup. */
public record CaregiverLookupResponse(
        UUID caregiverProfileId,
        String firstName,
        String lastName,
        String email,
        List<Qualification> qualifications,
        String city,
        String state,
        Integer serviceRadiusMiles,
        boolean alreadyOnRoster,
        String rosterStatus
) {
}
