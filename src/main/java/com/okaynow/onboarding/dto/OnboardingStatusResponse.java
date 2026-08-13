package com.okaynow.onboarding.dto;

import com.okaynow.users.domain.UserStatus;

import java.util.List;

public record OnboardingStatusResponse(
        UserStatus userStatus,
        boolean pendingReview,
        /** Profile + KYC requirements are satisfied; user may click Submit application. */
        boolean applicationReady,
        /** User confirmed submission; waiting for agency verification. */
        boolean applicationSubmitted,
        /** Alias of applicationSubmitted for waiting-room UI. */
        boolean applicationComplete,
        List<String> applicationMissing,
        String message,
        List<OnboardingRequestResponse> requests
) {
}
