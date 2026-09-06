package com.okaynow.agencies.dto;

import com.okaynow.agencies.domain.AgencyAccessStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SuperAdminUpdateAccessRequest(
        @NotNull AgencyAccessStatus accessStatus,
        @Size(max = 1000) String accessStatusNote
) {
}
