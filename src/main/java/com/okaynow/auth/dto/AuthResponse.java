package com.okaynow.auth.dto;

import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.UserStatus;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        String email,
        Role role,
        UserStatus status
) {
    public static AuthResponse bearer(String accessToken, String refreshToken, long expiresInSeconds,
                                      UUID userId, String email, Role role, UserStatus status) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInSeconds,
                userId, email, role, status);
    }
}
