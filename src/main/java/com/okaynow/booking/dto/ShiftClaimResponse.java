package com.okaynow.booking.dto;

import com.okaynow.booking.domain.ClaimSource;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.shifts.dto.ShiftResponse;

import java.time.Instant;
import java.util.UUID;

public record ShiftClaimResponse(
        UUID id,
        UUID caregiverProfileId,
        String caregiverFirstName,
        String caregiverLastName,
        String caregiverEmail,
        ShiftClaimStatus status,
        ClaimSource source,
        Instant claimedAt,
        Instant releasedAt,
        String cancelReason,
        ShiftResponse shift
) {
}
