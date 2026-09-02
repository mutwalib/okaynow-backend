package com.okaynow.agencies.dto;

import com.okaynow.agencies.domain.SubscriptionPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateSubscriptionPlanCatalogRequest(
        @NotBlank @Size(max = 64) String displayName,
        @Size(max = 500) String tagline,
        @NotNull List<@NotBlank @Size(max = 500) String> features,
        @Size(max = 32) String priceLabel,
        Integer sortOrder,
        Boolean enabled
) {
}
