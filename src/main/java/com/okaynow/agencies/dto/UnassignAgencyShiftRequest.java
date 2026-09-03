package com.okaynow.agencies.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UnassignAgencyShiftRequest(
        @NotNull UUID caregiverProfileId
) {
}
