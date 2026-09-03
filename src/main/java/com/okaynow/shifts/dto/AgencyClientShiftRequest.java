package com.okaynow.shifts.dto;

import com.okaynow.shifts.domain.ShiftScheduleType;
import com.okaynow.users.domain.Qualification;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record AgencyClientShiftRequest(
        @NotNull Qualification requiredQualification,
        LocalDate date,
        LocalDate endDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Size(max = 2000) String notes,
        ShiftScheduleType scheduleType,
        @jakarta.validation.constraints.Min(1)
        @jakarta.validation.constraints.Max(50)
        Integer requiredHeadcount,
        Boolean assignFromRoster
) {
}
