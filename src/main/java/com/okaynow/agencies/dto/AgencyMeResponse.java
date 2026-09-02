package com.okaynow.agencies.dto;

import com.okaynow.agencies.domain.SubscriptionPlan;
import com.okaynow.agencies.domain.SubscriptionStatus;
import com.okaynow.users.domain.Qualification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgencyMeResponse(
        UUID id,
        String slug,
        String legalName,
        String displayName,
        String licenseNumber,
        String addressLine,
        String city,
        String state,
        String zip,
        Double lat,
        Double lng,
        Integer serviceRadiusMiles,
        String publicDescription,
        List<Qualification> qualificationsSupported,
        SubscriptionStatus subscriptionStatus,
        SubscriptionPlan subscriptionPlan,
        Instant subscriptionPeriodStart,
        Instant subscriptionPeriodEnd,
        boolean directoryListed,
        boolean stripeConfigured,
        boolean stripeConnectReady,
        boolean subscriptionAllowsWrites
) {
}
