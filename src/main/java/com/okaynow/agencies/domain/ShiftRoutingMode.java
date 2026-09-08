package com.okaynow.agencies.domain;

/**
 * How home/facility openings are claimed and routed to caregivers.
 */
public enum ShiftRoutingMode {
    /** Opening waits in the agency inbox until a scheduler accepts, then broadcast/assign. */
    INBOX_FIRST,
    /** Opening is accepted and posted to area roster caregivers as soon as it is sent. */
    AUTO_BROADCAST
}
