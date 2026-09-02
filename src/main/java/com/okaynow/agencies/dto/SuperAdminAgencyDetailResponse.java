package com.okaynow.agencies.dto;

import com.okaynow.agencies.domain.SubscriptionPlan;
import com.okaynow.agencies.domain.SubscriptionStatus;
import com.okaynow.users.domain.Qualification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SuperAdminAgencyDetailResponse(
        UUID id,
        String slug,
        String legalName,
        String displayName,
        String licenseNumber,
        String addressLine,
        String city,
        String state,
        String zip,
        String publicDescription,
        List<Qualification> qualificationsSupported,
        SubscriptionStatus subscriptionStatus,
        SubscriptionPlan subscriptionPlan,
        boolean directoryListed,
        boolean hiringOpen,
        String hiringNote,
        Instant subscriptionPeriodStart,
        Instant subscriptionPeriodEnd,
        Instant createdAt,
        List<SuperAdminAgencyStaffResponse> staff
) {
}
