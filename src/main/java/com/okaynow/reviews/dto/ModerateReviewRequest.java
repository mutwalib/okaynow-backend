package com.okaynow.reviews.dto;

import com.okaynow.reviews.domain.ReviewStatus;
import jakarta.validation.constraints.NotNull;

public record ModerateReviewRequest(
        @NotNull ReviewStatus status
) {
}
