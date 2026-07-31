package com.okaynow.shifts.dto;

import com.okaynow.shifts.domain.ShiftScheduleType;
import com.okaynow.users.domain.Qualification;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateShiftRequest(
        UUID clientProfileId,
        /** Admin-only: attach a facility shift to a facility profile. Facilities ignore this. */
        UUID facilityProfileId,
        @NotNull Qualification requiredQualification,
        /**
         * Required for one-off shifts. Ignored for open-ended {@link ShiftScheduleType#DAILY_ROUTINE}
         * (coverage starts today and rolls forward).
         */
        LocalDate date,
        /**
         * Legacy bounded daily routines only. Open-ended daily routines omit this —
         * the system fills an ongoing horizon and extends it from the calendar.
         */
        LocalDate endDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotBlank String addressLine,
        @NotBlank String city,
        @Size(max = 2) String state,
        @NotBlank String zip,
        Double lat,
        Double lng,
        /** Required for admin-created shifts; ignored for clients (family/facility) — agency settings apply. */
        @DecimalMin("0.01") BigDecimal payRate,
        /** Required for admin-created shifts; ignored for clients (family/facility) — agency settings apply. */
        @DecimalMin("0.01") BigDecimal billRate,
        @Size(max = 2000) String notes,
        ShiftScheduleType scheduleType,
        /** Caregivers needed for this shift (default 1). */
        @jakarta.validation.constraints.Min(1)
        @jakarta.validation.constraints.Max(50)
        Integer requiredHeadcount,
        /**
         * When true and the shift has a family client, try to fill slots from the
         * client's caregiver roster (PRIMARY first, then ROTATIONAL).
         * Defaults to true for open-ended daily routines.
         */
        Boolean assignFromRoster
) {
}
