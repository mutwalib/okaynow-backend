package com.okaynow.shifts.domain;

public enum ShiftStatus {
    /** Created but not yet released to the caregiver marketplace. */
    DRAFT,
    /**
     * Previously open (or held for staffing) but pulled off the marketplace.
     * Distinct from DRAFT — not a new draft, just not visible to caregivers.
     */
    HELD,
    OPEN,
    CLAIMED,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}
