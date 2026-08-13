package com.okaynow.onboarding.dto;

import com.okaynow.users.domain.UserStatus;

import java.util.List;

public record OnboardingStatusResponse(
        UserStatus userStatus,
        boolean pendingReview,
        String message,
        List<OnboardingRequestResponse> requests
) {
}
