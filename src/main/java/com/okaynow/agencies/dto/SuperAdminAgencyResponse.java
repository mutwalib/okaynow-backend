package com.okaynow.agencies.dto;

import com.okaynow.agencies.domain.SubscriptionPlan;
import com.okaynow.agencies.domain.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

public record SuperAdminAgencyResponse(
        UUID id,
        String slug,
        String displayName,
        SubscriptionStatus subscriptionStatus,
        SubscriptionPlan subscriptionPlan,
        boolean directoryListed,
        Instant subscriptionPeriodEnd,
        Instant createdAt
) {
}
