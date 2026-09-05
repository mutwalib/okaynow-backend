package com.okaynow.users.domain;

public enum UserStatus {
    /** Registered; email OTP not completed. */
    PENDING_VERIFICATION,
    /**
     * Email verified; waiting for OkayNow review / onboarding info.
     * Caregivers and clients stay here until an admin approves.
     */
    PENDING_REVIEW,
    ACTIVE,
    /**
     * Can sign in and finish existing work, but cannot claim or receive new shifts.
     * Applied automatically after repeated no-show warnings; admins may lift to ACTIVE.
     */
    RESTRICTED,
    SUSPENDED,
    DEACTIVATED
}
