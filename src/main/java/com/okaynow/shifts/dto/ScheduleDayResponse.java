package com.okaynow.shifts.dto;

import java.time.LocalDate;
import java.util.List;

/** One day cell on the coverage calendar. */
public record ScheduleDayResponse(
        LocalDate date,
        List<ScheduleShiftCardResponse> shifts
) {
}
