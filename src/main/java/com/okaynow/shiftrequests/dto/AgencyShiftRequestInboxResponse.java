package com.okaynow.shiftrequests.dto;

import com.okaynow.shiftrequests.domain.ShiftRequestAgencyStatus;
import com.okaynow.shiftrequests.domain.ShiftRequestStatus;
import com.okaynow.users.domain.Qualification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Agency inbox row for a home-posted need. */
public record AgencyShiftRequestInboxResponse(
        UUID id,
        UUID shiftRequestId,
        ShiftRequestAgencyStatus status,
        ShiftRequestStatus requestStatus,
        UUID homeUserId,
        String clientFirstName,
        String clientLastName,
        Qualification requiredQualification,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        String city,
        String zip,
        String notes,
        Instant createdAt,
        UUID createdShiftId
) {
}
