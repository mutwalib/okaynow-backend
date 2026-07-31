package com.okaynow.admin.dto;

import com.okaynow.users.domain.CareRecipientRelationship;
import com.okaynow.users.domain.MedicaidEligibility;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateClientRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        String phone,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String addressLine,
        @NotBlank String city,
        @NotBlank @Size(min = 2, max = 2) String state,
        @NotBlank String zip,
        Double lat,
        Double lng,
        @Size(max = 2000) String careNeeds,
        @NotNull Boolean registeringForSelf,
        MedicaidEligibility medicaidEligible,
        CareRecipientRelationship relationshipToCareRecipient
) {
}
