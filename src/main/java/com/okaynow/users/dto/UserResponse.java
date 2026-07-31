package com.okaynow.users.dto;

import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String phone,
        Role role,
        UserStatus status,
        Instant createdAt
) {
}
