package com.okaynow.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PlatformConversionRequest(
        @NotNull UUID caregiverProfileId,
        String notes
) {
}
