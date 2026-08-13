package com.okaynow.users.domain;

public enum UserStatus {
    /** Registered; email OTP not completed. */
    PENDING_VERIFICATION,
    /**
     * Email verified; waiting for agency review / onboarding info.
     * Caregivers and clients stay here until an admin approves.
     */
    PENDING_REVIEW,
    ACTIVE,
    SUSPENDED,
    DEACTIVATED
}
