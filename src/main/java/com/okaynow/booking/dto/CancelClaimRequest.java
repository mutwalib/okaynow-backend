package com.okaynow.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelClaimRequest(
        @NotBlank(message = "A decline reason is required")
        @Size(max = 500)
        String cancelReason
) {
}
