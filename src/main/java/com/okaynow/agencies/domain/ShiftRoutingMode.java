package com.okaynow.agencies.domain;

/**
 * How accepted home/facility shift requests are routed to caregivers.
 */
public enum ShiftRoutingMode {
    /** Shift lands on the agency board; scheduler broadcasts or assigns manually. */
    INBOX_FIRST,
    /** Accepted shifts are posted to roster caregivers in the service area immediately. */
    AUTO_BROADCAST
}
