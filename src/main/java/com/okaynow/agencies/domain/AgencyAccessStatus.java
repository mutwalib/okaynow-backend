package com.okaynow.agencies.domain;

/**
 * Operational access for an agency tenant (separate from Stripe subscription status).
 * New signups start in {@link #PENDING_APPROVAL} until a platform admin approves them.
 */
public enum AgencyAccessStatus {
    /** Signed up; awaiting OkayNow review before console / write access. */
    PENDING_APPROVAL,
    /** Approved — full console subject to subscription rules. */
    ACTIVE,
    /** Temporary hold; staff can sign in but cannot use the console. */
    SUSPENDED,
    /** Hard block; staff can sign in but cannot use the console. */
    BLOCKED
}
