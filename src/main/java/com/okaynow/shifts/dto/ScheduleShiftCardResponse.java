package com.okaynow.shifts.dto;

import com.okaynow.booking.domain.ClaimSource;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.shifts.domain.ShiftScheduleType;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.users.domain.Qualification;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record ScheduleShiftCardResponse(
        UUID id,
        UUID clientProfileId,
        String clientLabel,
        Qualification requiredQualification,
        LocalTime startTime,
        LocalTime endTime,
        ShiftStatus status,
        ShiftScheduleType scheduleType,
        UUID seriesId,
        int requiredHeadcount,
        int filledSlots,
        int openSlots,
        boolean marketplacePosted,
        int marketplaceSlots,
        /** True when marketplace seats are open for claims. */
        boolean needsCoverage,
        String notes,
        List<ScheduleRosterSlotResponse> roster,
        /** True when the viewing agency created this shift (agency calendar only). */
        Boolean agencyManaged
) {
    public ScheduleShiftCardResponse {
        if (agencyManaged == null) {
            agencyManaged = false;
        }
    }
}
