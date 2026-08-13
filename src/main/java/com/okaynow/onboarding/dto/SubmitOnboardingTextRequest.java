package com.okaynow.onboarding.dto;

import jakarta.validation.constraints.Size;

public record SubmitOnboardingTextRequest(
        @Size(max = 4000) String responseText
) {
}
