package com.okaynow.booking.dto;

import java.util.UUID;

/** Caregiver the client/facility has connected with via roster or past shifts. */
public record PlatformConnectedCaregiverResponse(
        UUID caregiverProfileId,
        String firstName,
        String lastName
) {
}
