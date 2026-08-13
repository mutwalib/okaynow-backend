package com.okaynow.auth.dto;

import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.UserStatus;

import java.util.UUID;

/**
 * Login may complete with tokens, or pause for an email OTP (ADMIN / agency).
 */
public record LoginResult(
        boolean requiresOtp,
        String email,
        String message,
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresInSeconds,
        UUID userId,
        Role role,
        UserStatus status
) {
    public static LoginResult otpRequired(String email) {
        return new LoginResult(
                true,
                email,
                "Enter the one-time code we emailed you to finish signing in.",
                null, null, null, null, null, null, null);
    }

    public static LoginResult tokens(AuthResponse auth) {
        return new LoginResult(
                false,
                auth.email(),
                null,
                auth.accessToken(),
                auth.refreshToken(),
                auth.tokenType(),
                auth.expiresInSeconds(),
                auth.userId(),
                auth.role(),
                auth.status());
    }
}
