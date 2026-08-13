package com.okaynow.onboarding.dto;

import com.okaynow.onboarding.domain.OnboardingFieldType;
import com.okaynow.onboarding.domain.OnboardingRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record OnboardingRequestResponse(
        UUID id,
        String title,
        String instructions,
        OnboardingFieldType fieldType,
        OnboardingRequestStatus status,
        String responseText,
        String fileUrl,
        Instant createdAt,
        Instant submittedAt
) {
}
