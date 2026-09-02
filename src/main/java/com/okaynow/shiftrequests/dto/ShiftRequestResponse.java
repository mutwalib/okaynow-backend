package com.okaynow.shiftrequests.dto;

import com.okaynow.shiftrequests.domain.ShiftRequestAgencyStatus;
import com.okaynow.shiftrequests.domain.ShiftRequestStatus;
import com.okaynow.users.domain.Qualification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record ShiftRequestResponse(
        UUID id,
        ShiftRequestStatus status,
        Qualification requiredQualification,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        String addressLine,
        String city,
        String state,
        String zip,
        String notes,
        Instant createdAt,
        List<TargetAgencyResponse> targetAgencies
) {
    public record TargetAgencyResponse(
            UUID agencyId,
            String agencyDisplayName,
            ShiftRequestAgencyStatus status,
            UUID createdShiftId
    ) {
    }
}
