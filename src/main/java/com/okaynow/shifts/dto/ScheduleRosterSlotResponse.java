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
        ClaimSource source,
        String profilePhotoUrl,
        boolean masked,
        String displayLabel
) {
    public static ScheduleRosterSlotResponse visible(
            UUID claimId,
            UUID caregiverProfileId,
            String firstName,
            String lastName,
            ShiftClaimStatus status,
            ClaimSource source,
            String profilePhotoUrl) {
        return new ScheduleRosterSlotResponse(
                claimId,
                caregiverProfileId,
                firstName,
                lastName,
                status,
                source,
                profilePhotoUrl,
                false,
                null);
    }

    public static ScheduleRosterSlotResponse masked(UUID claimId, ShiftClaimStatus status, ClaimSource source) {
        return new ScheduleRosterSlotResponse(
                claimId,
                null,
                null,
                null,
                status,
                source,
                null,
                true,
                "Occupied by other");
    }
}
