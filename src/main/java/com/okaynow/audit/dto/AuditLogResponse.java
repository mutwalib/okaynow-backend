package com.okaynow.audit.dto;

import com.okaynow.audit.domain.AuditAction;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID actorUserId,
        String actorEmail,
        AuditAction action,
        String entityType,
        UUID entityId,
        UUID clientProfileId,
        String details,
        Instant createdAt
) {
}
