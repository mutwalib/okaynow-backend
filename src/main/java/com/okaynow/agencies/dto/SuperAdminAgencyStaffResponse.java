package com.okaynow.agencies.dto;

import com.okaynow.agencies.domain.AgencyStaffRole;
import com.okaynow.users.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record SuperAdminAgencyStaffResponse(
        UUID staffId,
        UUID userId,
        String email,
        String displayName,
        UserStatus status,
        AgencyStaffRole staffRole,
        Instant joinedAt
) {
}
