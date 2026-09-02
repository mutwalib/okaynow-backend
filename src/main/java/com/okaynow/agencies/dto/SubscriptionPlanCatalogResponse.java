package com.okaynow.agencies.dto;

import com.okaynow.agencies.domain.SubscriptionPlan;

import java.util.List;

public record SubscriptionPlanCatalogResponse(
        SubscriptionPlan plan,
        String displayName,
        String tagline,
        List<String> features,
        int monthlyPriceCents,
        String priceDisplay,
        int sortOrder,
        boolean enabled
) {
}
