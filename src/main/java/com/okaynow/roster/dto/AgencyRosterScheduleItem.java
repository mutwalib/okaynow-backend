package com.okaynow.roster.dto;

import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.users.domain.Qualification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AgencyRosterScheduleItem(
        UUID shiftId,
        UUID claimId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String city,
        Qualification requiredQualification,
        ShiftStatus shiftStatus,
        ShiftClaimStatus claimStatus,
        /** Shift fully staffed (filledSlots >= requiredHeadcount). */
        boolean fulfilled,
        int filledSlots,
        int requiredHeadcount,
        /** Short agency-facing next step, if any. */
        String callToAction
) {
}
