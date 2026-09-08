package com.okaynow.notifications.domain;

public enum NotificationType {
    SHIFT_DRAFT_CREATED,
    SHIFT_POSTED,
    SHIFT_CLAIMED,
    SHIFT_ASSIGNED,
    SHIFT_INVITED,
    SHIFT_INVITE_ACCEPTED,
    SHIFT_INVITE_DECLINED,
    SHIFT_INVITE_FAILED,
    SHIFT_CONFIRMED,
    SHIFT_RELEASED,
    SHIFT_HELD,
    SHIFT_CANCELLED,
    SHIFT_STARTED,
    SHIFT_COMPLETED,
    SHIFT_EXTENDED,
    SHIFT_REPLACEMENT_REQUESTED,
    CAREGIVER_REJECTED_BY_CLIENT,
    SHIFT_NO_SHOW,
    /** Formal no-show warning issued to a caregiver (and mirrored to super admins). */
    CAREGIVER_NO_SHOW_WARNING,
    /** Caregiver auto-restricted after repeated no-show warnings. */
    CAREGIVER_AUTO_RESTRICTED,
    PLATFORM_CONVERSION_FEE,
    SHIFT_SURGE_APPLIED,
    SHIFT_ESCALATION_ALERT,
    VISIT_CLOCK_IN,
    VISIT_CLOCK_OUT,
    VISIT_ARRIVAL_CONFIRMED,
    INVOICE_SENT,
    ONBOARDING_INFO_REQUESTED,
    ACCOUNT_APPROVED,
    /** Agency invited caregiver to roster (with pay offer). */
    ROSTER_INVITE,
    /** Agency revised the agreed pay offer on an existing roster row. */
    ROSTER_PAY_OFFER_UPDATED,
    /** Agency removed caregiver from roster or cancelled a pending invite. */
    ROSTER_REMOVED,
    /** Home/facility sent an opening to this agency (inbox). */
    SHIFT_REQUEST_RECEIVED,
    /** Agency accepted a home/facility opening (manual Accept). */
    SHIFT_REQUEST_ACCEPTED,
    /** AUTO_BROADCAST accepted and posted the opening without inbox click. */
    SHIFT_REQUEST_AUTO_ACCEPTED,
    SYSTEM
}
