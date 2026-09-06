package com.okaynow.roster.dto;

import com.okaynow.roster.domain.AgencyCaregiverStatus;

import java.math.BigDecimal;
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
        BigDecimal agreedPayRate,
        String payOfferNote,
        Instant payOfferUpdatedAt,
        Instant invitedAt,
        Instant respondedAt
) {
}
