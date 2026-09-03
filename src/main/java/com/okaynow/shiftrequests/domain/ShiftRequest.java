package com.okaynow.shiftrequests.domain;

import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.FacilityProfile;
import com.okaynow.users.domain.Qualification;
import com.okaynow.users.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import jakarta.persistence.EntityListeners;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "shift_requests")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "home_user_id", nullable = false)
    private User homeUser;

    /** Home-posted needs; null when the requester is a facility. */
    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "client_profile_id")
    private ClientProfile clientProfile;

    /** Facility-posted needs; null when the requester is a home. */
    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_profile_id")
    private FacilityProfile facilityProfile;

    /** Facility calendar shift this opening was sent from, when applicable. */
    @Column(name = "source_shift_id")
    private UUID sourceShiftId;

    @Column(nullable = false, columnDefinition = "integer default 1")
    @Builder.Default
    private int requiredHeadcount = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Qualification requiredQualification;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    private String addressLine;

    private String city;

    @Builder.Default
    private String state = "MA";

    private String zip;

    @Column(length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ShiftRequestStatus status = ShiftRequestStatus.OPEN;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
