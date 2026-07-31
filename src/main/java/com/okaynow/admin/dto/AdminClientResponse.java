package com.okaynow.admin.dto;

import com.okaynow.users.domain.CareRecipientRelationship;
import com.okaynow.users.domain.MedicaidEligibility;
import com.okaynow.users.domain.UserStatus;

import java.util.UUID;

public record AdminClientResponse(
        UUID id,
        ClientType clientType,
        /** Facility display name; null for family clients. */
        String facilityName,
        UUID userId,
        String email,
        String phone,
        UserStatus status,
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
        boolean canDeleteShifts
) {
}
