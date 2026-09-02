package com.okaynow.agencies.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateSubscriptionPlanCatalogRequest(
        @NotBlank @Size(max = 64) String displayName,
        @Size(max = 500) String tagline,
        @NotNull List<@NotBlank @Size(max = 500) String> features,
        @Min(50) int monthlyPriceCents,
        Integer sortOrder,
        Boolean enabled
) {
}
