package com.okaynow.auth.dto;

import com.okaynow.users.domain.CareRecipientRelationship;
import com.okaynow.users.domain.MedicaidEligibility;
import com.okaynow.users.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        String phone,
        @NotNull Role role,
        @NotBlank String firstName,
        @NotBlank String lastName,
        Boolean registeringForSelf,
        MedicaidEligibility medicaidEligible,
        CareRecipientRelationship relationshipToCareRecipient,
        String facilityName,
        String addressLine,
        String city,
        @Size(max = 2) String state,
        String zip,
        /** Published legal document IDs the user accepted at registration. */
        java.util.List<java.util.UUID> acceptedLegalDocumentIds
) {
}
