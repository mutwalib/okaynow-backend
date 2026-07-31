package com.okaynow.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignCaregiverRequest(
        @NotNull UUID caregiverProfileId
) {
}
