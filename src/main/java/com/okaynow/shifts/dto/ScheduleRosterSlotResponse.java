package com.okaynow.shifts.dto;

import com.okaynow.booking.domain.ClaimSource;
import com.okaynow.booking.domain.ShiftClaimStatus;

import java.util.UUID;

public record ScheduleRosterSlotResponse(
        UUID claimId,
        UUID caregiverProfileId,
        String firstName,
        String lastName,
        ShiftClaimStatus status,
        ClaimSource source
) {
}
