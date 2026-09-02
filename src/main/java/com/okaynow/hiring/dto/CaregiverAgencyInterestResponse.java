package com.okaynow.hiring.dto;

import com.okaynow.hiring.domain.CaregiverAgencyInterestStatus;
import com.okaynow.users.domain.Qualification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaregiverAgencyInterestResponse(
        UUID id,
        UUID agencyId,
        String agencyDisplayName,
        String agencyCity,
        String agencyState,
        boolean agencyHiringOpen,
        UUID caregiverProfileId,
        String caregiverFirstName,
        String caregiverLastName,
        String caregiverEmail,
        List<Qualification> qualifications,
        CaregiverAgencyInterestStatus status,
        String message,
        Instant createdAt,
        Instant respondedAt
) {
}
