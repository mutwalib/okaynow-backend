package com.okaynow.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignFromRosterRequest(
        @NotNull UUID caregiverProfileId
) {
}
