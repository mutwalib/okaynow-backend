package com.okaynow.marketplace.dto;

import com.okaynow.marketplace.domain.CredentialType;
import com.okaynow.marketplace.domain.CredentialVerificationStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UpsertCaregiverCredentialRequest(
        @NotNull CredentialType credentialType,
        String licenseNumber,
        LocalDate issueDate,
        LocalDate expiryDate,
        String documentUrl,
        CredentialVerificationStatus verificationStatus
) {
}
