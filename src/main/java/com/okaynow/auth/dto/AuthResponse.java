package com.okaynow.auth.dto;

import com.okaynow.users.domain.Role;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        String email,
        Role role
) {
    public static AuthResponse bearer(String accessToken, String refreshToken, long expiresInSeconds,
                                      UUID userId, String email, Role role) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, userId, email, role);
    }
}
