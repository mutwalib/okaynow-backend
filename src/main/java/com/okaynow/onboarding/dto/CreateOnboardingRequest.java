package com.okaynow.onboarding.dto;

import com.okaynow.onboarding.domain.OnboardingFieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOnboardingRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String instructions,
        @NotNull OnboardingFieldType fieldType
) {
}
