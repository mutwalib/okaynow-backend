package com.okaynow.roster.domain;

/**
 * How this caregiver is paid for work under this agency roster relationship.
 */
public enum RosterPayClassification {
    /** Agency runs W-2 payroll and withholds taxes. */
    W2,
    /** Not W-2 (e.g. other arrangement as agreed with the caregiver). */
    NON_W2
}
