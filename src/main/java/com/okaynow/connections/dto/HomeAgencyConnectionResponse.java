package com.okaynow.connections.dto;

import com.okaynow.connections.domain.ConnectionStatus;

import java.time.Instant;
import java.util.UUID;

public record HomeAgencyConnectionResponse(
        UUID id,
        UUID agencyId,
        String agencySlug,
        String agencyDisplayName,
        String agencyCity,
        String agencyState,
        UUID homeUserId,
        UUID clientProfileId,
        String homeFirstName,
        String homeLastName,
        ConnectionStatus status,
        String homeMessage,
        Instant createdAt,
        Instant respondedAt
) {
}
