package com.okaynow.booking.domain;

public enum ShiftClaimStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    /** Shift window ended without completion / cancellation. */
    EXPIRED
}
