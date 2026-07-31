package com.okaynow.evv.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Client-assisted attendance when the caregiver missed self clock-in/out. */
public record ClientAttendanceRequest(
        @NotNull Instant clockInAt,
        Instant clockOutAt,
        String notes
) {
}
