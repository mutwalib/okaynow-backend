package com.okaynow.agencies.dto;

import com.okaynow.agencies.domain.SubscriptionPlan;

import java.util.List;

public record SubscriptionPlanCatalogResponse(
        SubscriptionPlan plan,
        String displayName,
        String tagline,
        List<String> features,
        String priceLabel,
        int sortOrder,
        boolean enabled
) {
}
