package com.okaynow.roster.dto;

import com.okaynow.roster.domain.AgencyCaregiverStatus;

import java.time.Instant;
import java.util.UUID;

public record AgencyRosterEntryResponse(
        UUID id,
        UUID agencyId,
        String agencyDisplayName,
        UUID caregiverProfileId,
        String caregiverFirstName,
        String caregiverLastName,
        String caregiverEmail,
        AgencyCaregiverStatus status,
        String inviteMessage,
        Instant invitedAt,
        Instant respondedAt
) {
}
