package com.okaynow.staffing.domain;

/**
 * How a caregiver is attached to a client for recruiting / coverage.
 * Assignments do not lock individual shifts — OPEN shifts remain claimable
 * by any eligible caregiver in jurisdiction until filled.
 */
public enum AssignmentType {
    /** Dedicated single caregiver for this client. */
    PRIMARY,
    /** Shared rotational pool across this and/or other clients. */
    ROTATIONAL
}
