package com.okaynow.shifts.domain;

import com.okaynow.users.domain.Qualification;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A bookable unit of work. Carries both payRate (caregiver) and billRate (client)
 * per the bill-rate markup model (CLAUDE.md Section 2).
 */
@Entity
@Table(name = "shifts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_profile_id")
    private UUID clientProfileId;

    /** Set for facility-posted shifts; mutually exclusive with clientProfileId for requester roles. */
    @Column(name = "facility_profile_id")
    private UUID facilityProfileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Qualification requiredQualification;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private int durationMinutes = 0;

    @Column(nullable = false)
    private String addressLine;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    @Builder.Default
    private String state = "MA";

    @Column(nullable = false)
    private String zip;

    private Double lat;

    private Double lng;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal payRate;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal billRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ShiftStatus status = ShiftStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private ShiftScheduleType scheduleType = ShiftScheduleType.ONE_OFF;

    /** Shared across all day instances of a DAILY_ROUTINE series. */
    private UUID seriesId;

    /**
     * Ongoing daily routine (no end date): calendar materializes future days
     * from this series as needed. Call-out opens marketplace for one date only.
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean openEnded = false;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean platformPaid = false;

    /**
     * True once an admin has released the shift to the open caregiver board.
     * Direct admin assignment from DRAFT leaves this false so the shift never
     * becomes publicly claimable unless later published.
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean marketplacePosted = false;

    /**
     * How many slots are currently open for marketplace claims.
     * Clients choose this when posting coverage (partial remaining headcount).
     */
    @Column(nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private int marketplaceSlots = 0;

    /** How many caregivers are needed for this shift (default 1). */
    @Column(nullable = false, columnDefinition = "integer default 1")
    @Builder.Default
    private int requiredHeadcount = 1;

    /** Active claims (PENDING + CONFIRMED) currently filling slots. */
    @Column(nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private int filledSlots = 0;

    /**
     * Extra $/hr paid to the caregiver when facility escalation applies.
     * Mirrored onto billRate so the facility funds the surge.
     */
    @Column(nullable = false, precision = 8, scale = 2, columnDefinition = "numeric(8,2) default 0")
    @Builder.Default
    private BigDecimal surgeBonusPay = BigDecimal.ZERO;

    /** Highest escalation tier applied (0 = none, 1–3). */
    @Column(nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private int surgeTierApplied = 0;

    /** Temporary radius expand (miles) from facility escalation. */
    @Column(nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private int escalationRadiusBonusMiles = 0;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    /** Tenant agency that owns this shift (null = legacy marketplace shift). */
    @Column(name = "agency_id")
    private UUID agencyId;

    /** Home-initiated need that produced this shift, when applicable. */
    @Column(name = "shift_request_id")
    private UUID shiftRequestId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @jakarta.persistence.PrePersist
    @jakarta.persistence.PreUpdate
    private void calculateDuration() {
        if (startTime != null && endTime != null) {
            durationMinutes = com.okaynow.evv.support.ShiftWindows.durationMinutes(startTime, endTime);
        }
    }
}
