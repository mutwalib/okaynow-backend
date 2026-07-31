package com.okaynow.booking.dto;

import jakarta.validation.constraints.Size;

public record RejectCaregiverRequest(
        @Size(max = 500) String reason
) {
}
