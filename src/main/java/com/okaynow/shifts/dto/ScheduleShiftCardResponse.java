package com.okaynow.shifts.dto;

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
        /**
         * Agency calendar only: true = this agency owns the shift;
         * false = another agency's coverage (opaque); null = home/facility calendar.
         */
        Boolean agencyManaged,
        /** Facility opening was sent to connected agencies. */
        Boolean agencyCoverageRequested,
        /** Staffing agency name when assigned, or pending target when coverage was requested. */
        String agencyDisplayName
) {
    public ScheduleShiftCardResponse {
        if (agencyCoverageRequested == null) {
            agencyCoverageRequested = false;
        }
    }
}
