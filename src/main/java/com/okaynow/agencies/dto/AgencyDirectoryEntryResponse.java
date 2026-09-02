package com.okaynow.agencies.dto;

import com.okaynow.agencies.domain.SubscriptionPlan;
import com.okaynow.users.domain.Qualification;

import java.util.List;
import java.util.UUID;

public record AgencyDirectoryEntryResponse(
        UUID id,
        String slug,
        String displayName,
        String city,
        String state,
        String zip,
        Double lat,
        Double lng,
        Double distanceMiles,
        SubscriptionPlan subscriptionPlan,
        List<Qualification> qualificationsSupported,
        String publicDescriptionSnippet,
        boolean hiringOpen,
        String hiringNote
) {
}
