package com.okaynow.staffing.dto;

/**
 * Result of adding/removing a caregiver on a client roster, including optional
 * schedule side-effects (fill opens / clear future claims).
 */
public record ClientRosterChangeResponse(
        ClientCaregiverAssignmentResponse assignment,
        int openShiftsFilled,
        int scheduleClaimsReleased
) {
}
