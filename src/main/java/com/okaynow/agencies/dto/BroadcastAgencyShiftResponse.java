package com.okaynow.agencies.dto;

import java.util.UUID;

public record BroadcastAgencyShiftResponse(
        /** ROSTER_OPEN = posted to roster board; INVITED = direct invites sent. */
        String mode,
        int recipientsNotified,
        UUID shiftId
) {
}
