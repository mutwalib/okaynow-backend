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
        /** True when ACTIVE or INVITED — cannot send another invite. */
        boolean alreadyOnRoster,
        /** True when REMOVED — agency can send a new invite that requires accept. */
        boolean canReinvite,
        String rosterStatus
) {
}
