package com.okaynow.admin.dto;

import com.okaynow.agencies.domain.AgencyStaffRole;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String phone,
        Role role,
        UserStatus status,
        boolean emailVerified,
        String displayName,
        UUID agencyId,
        String agencySlug,
        String agencyDisplayName,
        AgencyStaffRole agencyStaffRole,
        Instant createdAt
) {
}
