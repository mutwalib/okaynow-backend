package com.okaynow.agencies.dto;

import com.okaynow.agencies.domain.AgencyAccessStatus;
import com.okaynow.agencies.domain.SubscriptionPlan;
import com.okaynow.agencies.domain.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

public record SuperAdminAgencyResponse(
        UUID id,
        String slug,
        String displayName,
        String city,
        String state,
        AgencyAccessStatus accessStatus,
        SubscriptionStatus subscriptionStatus,
        SubscriptionPlan subscriptionPlan,
        boolean directoryListed,
        boolean hiringOpen,
        long staffCount,
        Instant subscriptionPeriodEnd,
        Instant createdAt
) {
}
