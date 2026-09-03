package com.okaynow.agencies.dto;

import com.okaynow.booking.domain.ShiftClaimStatus;

import java.util.UUID;

public record AgencyShiftAssignmentResponse(
        UUID claimId,
        UUID caregiverProfileId,
        String firstName,
        String lastName,
        ShiftClaimStatus status
) {
}
