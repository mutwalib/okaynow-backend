package com.okaynow.agencies.dto;

import com.okaynow.agencies.domain.SubscriptionPlan;
import com.okaynow.agencies.domain.SubscriptionStatus;

import java.time.Instant;

public record SuperAdminUpdateSubscriptionRequest(
        SubscriptionStatus subscriptionStatus,
        SubscriptionPlan subscriptionPlan,
        Boolean directoryListed,
        Instant subscriptionPeriodEnd
) {
}
