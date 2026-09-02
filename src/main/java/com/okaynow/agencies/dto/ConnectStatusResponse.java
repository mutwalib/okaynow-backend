package com.okaynow.agencies.dto;

public record ConnectStatusResponse(
        boolean stripeConfigured,
        boolean hasConnectAccount,
        boolean chargesEnabled,
        boolean payoutsEnabled,
        boolean onboardingComplete
) {
}
