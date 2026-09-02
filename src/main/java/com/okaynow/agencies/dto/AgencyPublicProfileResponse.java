package com.okaynow.agencies.dto;

import com.okaynow.agencies.domain.SubscriptionPlan;
import com.okaynow.agencies.domain.SubscriptionStatus;
import com.okaynow.users.domain.Qualification;

import java.util.List;
import java.util.UUID;

public record AgencyPublicProfileResponse(
        UUID id,
        String slug,
        String displayName,
        String legalName,
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
        SubscriptionPlan subscriptionPlan,
        SubscriptionStatus subscriptionStatus,
        boolean directoryListed,
        boolean hiringOpen,
        String hiringNote
) {
}
