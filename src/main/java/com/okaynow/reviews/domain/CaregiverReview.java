package com.okaynow.reviews.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Client (family or facility) rating of a caregiver after a completed shift.
 * Only {@link ReviewStatus#PUBLISHED} reviews appear on the caregiver profile.
 */
@Entity
@Table(
        name = "caregiver_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_caregiver_reviews_shift", columnNames = "shift_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaregiverReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID shiftId;

    @Column(nullable = false)
    private UUID shiftClaimId;

    @Column(nullable = false)
    private UUID caregiverProfileId;

    @Column(nullable = false)
    private UUID reviewerUserId;

    private UUID clientProfileId;

    private UUID facilityProfileId;

    @Column(nullable = false)
    private int rating;

    @Column(length = 2000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant moderatedAt;

    private UUID moderatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
