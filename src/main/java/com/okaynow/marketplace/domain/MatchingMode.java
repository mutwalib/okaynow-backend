package com.okaynow.marketplace.domain;

/**
 * How caregivers are matched to shifts for a qualification rule pack.
 */
public enum MatchingMode {
    /** Great-circle miles vs caregiver service radius. */
    RADIUS,
    /** Estimated drive-time minutes vs maxDriveMinutes. */
    DRIVE_TIME
}
