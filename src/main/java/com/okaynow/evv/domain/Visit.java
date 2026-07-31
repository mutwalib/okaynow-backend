package com.okaynow.evv.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Attendance / EVV-ready visit record for a claimed shift.
 * Caregiver clocks in; client confirms the caregiver reported on site.
 */
@Entity
@Table(name = "visits", uniqueConstraints = {
        @UniqueConstraint(name = "uk_visits_shift", columnNames = "shift_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "caregiver_profile_id", nullable = false)
    private UUID caregiverProfileId;

    @Column(nullable = false)
    private Instant clockInAt;

    private Double clockInLat;

    private Double clockInLng;

    private Instant clockOutAt;

    private Double clockOutLat;

    private Double clockOutLng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ClockMethod method = ClockMethod.GPS;

    @Column(nullable = false)
    @Builder.Default
    private boolean clientArrivalConfirmed = false;

    private Instant clientArrivalConfirmedAt;

    private UUID clientArrivalConfirmedByUserId;

    @Column(length = 500)
    private String notes;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
