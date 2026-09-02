package com.okaynow.booking.domain;

public enum ClaimSource {
    /** Caregiver self-claimed an OPEN marketplace shift. */
    MARKETPLACE,
    /** Agency recruited / assigned the caregiver to the shift (auto-confirmed). */
    ASSIGNED,
    /** Caregiver self-claimed an agency roster-open shift (tenant-scoped board). */
    ROSTER_OPEN,
    /**
     * Private invitation to a specific caregiver. Starts PENDING until the caregiver
     * accepts (→ CONFIRMED) or declines (→ CANCELLED). Not visible on the open board.
     */
    INVITE
}
