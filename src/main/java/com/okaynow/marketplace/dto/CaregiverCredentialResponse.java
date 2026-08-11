package com.okaynow.marketplace.dto;

import com.okaynow.marketplace.domain.CredentialType;
import com.okaynow.marketplace.domain.CredentialVerificationStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CaregiverCredentialResponse(
        UUID id,
        UUID caregiverProfileId,
        CredentialType credentialType,
        String licenseNumber,
        LocalDate issueDate,
        LocalDate expiryDate,
        String documentUrl,
        CredentialVerificationStatus verificationStatus,
        String primarySourceStatus,
        Instant primarySourceCheckedAt,
        String primarySourceNotes,
        UUID reviewedBy,
        Instant reviewedAt,
        Instant createdAt
) {
}
