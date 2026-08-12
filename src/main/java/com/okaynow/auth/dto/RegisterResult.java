package com.okaynow.auth.dto;

public record RegisterResult(
        boolean requiresEmailVerification,
        String email,
        String message
) {
    public static RegisterResult pending(String email) {
        return new RegisterResult(
                true,
                email,
                "We sent a verification code to your email. Enter it to activate your account.");
    }
}
