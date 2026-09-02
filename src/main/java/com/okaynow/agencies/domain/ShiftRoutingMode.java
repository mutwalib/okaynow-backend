package com.okaynow.agencies.domain;

/**
 * How incoming home shift requests are routed to caregivers.
 */
public enum ShiftRoutingMode {
    /** Shift lands in agency inbox; scheduler broadcasts or assigns manually. */
    INBOX_FIRST,
    /** Accepted shifts are posted to roster caregivers in the service area immediately. */
    AUTO_BROADCAST
}
