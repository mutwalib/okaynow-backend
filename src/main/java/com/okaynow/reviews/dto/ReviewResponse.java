package com.okaynow.reviews.dto;

import com.okaynow.reviews.domain.ReviewStatus;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID shiftId,
        UUID shiftClaimId,
        UUID caregiverProfileId,
        String caregiverFirstName,
        String caregiverLastName,
        UUID reviewerUserId,
        String reviewerLabel,
        UUID clientProfileId,
        UUID facilityProfileId,
        int rating,
        String comment,
        ReviewStatus status,
        Instant createdAt,
        Instant moderatedAt
) {
}
