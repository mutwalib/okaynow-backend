package com.okaynow.hiring.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ExpressInterestRequest(
        UUID agencyId,
        @Size(max = 2000) String message
) {
}
