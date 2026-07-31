package com.okaynow.evv.dto;

import com.okaynow.evv.domain.ClockMethod;

import java.time.Instant;
import java.util.UUID;

public record VisitResponse(
        UUID id,
        UUID shiftId,
        UUID claimId,
        UUID caregiverProfileId,
        String caregiverFirstName,
        String caregiverLastName,
        Instant clockInAt,
        Double clockInLat,
        Double clockInLng,
        Instant clockOutAt,
        Double clockOutLat,
        Double clockOutLng,
        ClockMethod method,
        boolean clientArrivalConfirmed,
        Instant clientArrivalConfirmedAt,
        String notes
) {
}
