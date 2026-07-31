package com.okaynow.shifts.dto;

import com.okaynow.shifts.domain.ShiftScheduleType;

import java.util.List;
import java.util.UUID;

public record CreateShiftResponse(
        ShiftScheduleType scheduleType,
        UUID seriesId,
        int createdCount,
        /** Days skipped because they overlapped an existing shift (daily routines only). */
        int skippedOverlapCount,
        List<ShiftResponse> shifts
) {
}
