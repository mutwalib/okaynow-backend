package com.okaynow.booking.domain;

import com.okaynow.shifts.domain.Shift;
import com.okaynow.users.domain.CaregiverProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Join of Caregiver + Shift. Rows are never deleted: cancelled/released claims stay
 * as booking history for a shift (audit + dispute trail).
 */
@Entity
@Table(name = "shift_claims")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "caregiver_profile_id", nullable = false)
    private CaregiverProfile caregiverProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ShiftClaimStatus status = ShiftClaimStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private ClaimSource source = ClaimSource.MARKETPLACE;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant claimedAt;

    private Instant releasedAt;

    @Column(length = 500)
    private String cancelReason;
}
