package com.okaynow.shifts.dto;

import com.okaynow.shifts.domain.ShiftScheduleType;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.users.domain.Qualification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ShiftResponse(
        UUID id,
        UUID clientProfileId,
        UUID facilityProfileId,
        Qualification requiredQualification,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String addressLine,
        String city,
        String state,
        String zip,
        Double lat,
        Double lng,
        BigDecimal payRate,
        BigDecimal billRate,
        ShiftStatus status,
        ShiftScheduleType scheduleType,
        UUID seriesId,
        String notes,
        boolean platformPaid,
        boolean marketplacePosted,
        int marketplaceSlots,
        int requiredHeadcount,
        int filledSlots,
        java.math.BigDecimal surgeBonusPay,
        int surgeTierApplied,
        int escalationRadiusBonusMiles,
        UUID createdBy,
        Instant createdAt,
        boolean agencyCoverageRequested
) {
}
