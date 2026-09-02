package com.okaynow.roster.domain;

public enum AgencyCaregiverStatus {
    INVITED,
    ACTIVE,
    SUSPENDED,
    /** Former roster member — record retained for agency history. */
    REMOVED
}
