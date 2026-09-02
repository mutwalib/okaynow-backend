package com.okaynow.agencies.dto;

import java.util.List;
import java.util.UUID;

public record BroadcastAgencyShiftRequest(
        /** When empty, post openly to all active roster caregivers in the service area. */
        List<UUID> caregiverProfileIds
) {
    public BroadcastAgencyShiftRequest {
        caregiverProfileIds = caregiverProfileIds == null ? List.of() : List.copyOf(caregiverProfileIds);
    }
}
