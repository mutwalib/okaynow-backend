package com.okaynow.users.dto;

import com.okaynow.users.domain.CareRecipientRelationship;
import com.okaynow.users.domain.MedicaidEligibility;

import java.util.UUID;

public record ClientProfileResponse(
        UUID id,
        UUID userId,
        String firstName,
        String lastName,
        String addressLine,
        String city,
        String state,
        String zip,
        Double lat,
        Double lng,
        String careNeeds,
        boolean registeringForSelf,
        MedicaidEligibility medicaidEligible,
        CareRecipientRelationship relationshipToCareRecipient,
        boolean canViewShifts,
        boolean canCreateShifts,
        boolean canUpdateShifts,
        boolean canDeleteShifts,
        String profilePhotoUrl
) {
}
