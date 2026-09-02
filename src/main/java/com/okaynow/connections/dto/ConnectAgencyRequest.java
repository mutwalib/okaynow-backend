package com.okaynow.connections.dto;

import jakarta.validation.constraints.Size;

public record ConnectAgencyRequest(
        @Size(max = 1000) String message
) {
}
