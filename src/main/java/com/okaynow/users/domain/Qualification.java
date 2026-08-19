package com.okaynow.users.domain;

/**
 * Caregiver qualification types supported in MA.
 */
public enum Qualification {
    CNA,
    HHA,
    PCA,
    LPN,
    RN,
    /** Medication Administration Program (MA MAP) certification. */
    MAP,
    /** Other / not specified — agency reviews during KYC. */
    OTHER
}
