package com.okaynow.agencies.dto;

import com.okaynow.agencies.domain.PlanCapability;
import com.okaynow.agencies.domain.SubscriptionPlan;

public record PlanCapabilityResponse(
        String code,
        String label,
        String category,
        SubscriptionPlan introducedIn,
        boolean inheritSummary,
        boolean recommended
) {
    public static PlanCapabilityResponse of(PlanCapability capability, SubscriptionPlan editingPlan) {
        return new PlanCapabilityResponse(
                capability.name(),
                capability.getLabel(),
                capability.getCategory(),
                capability.getIntroducedIn(),
                capability.isInheritSummary(),
                PlanCapability.recommendedFor(editingPlan).contains(capability));
    }
}
