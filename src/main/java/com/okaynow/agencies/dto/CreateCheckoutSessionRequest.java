package com.okaynow.agencies.dto;

import com.okaynow.agencies.domain.SubscriptionPlan;

public record CreateCheckoutSessionRequest(
        SubscriptionPlan plan
) {
}
