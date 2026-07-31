package com.okaynow.admin.dto;

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
        Instant createdAt
) {
}
