package com.okaynow.payroll.domain;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ledger row for a completed shift: client → agency (bill) and agency → caregiver (pay).
 */
@Entity
@Table(name = "shift_settlements", uniqueConstraints = {
        @UniqueConstraint(name = "uk_shift_settlements_shift", columnNames = "shift_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    @Column(name = "claim_id")
    private UUID claimId;

    @Column(name = "caregiver_profile_id", nullable = false)
    private UUID caregiverProfileId;

    @Column(name = "client_profile_id")
    private UUID clientProfileId;

    /** Bill-to facility when the shift was posted by a facility (mutually exclusive with family client). */
    @Column(name = "facility_profile_id")
    private UUID facilityProfileId;

    @Column(nullable = false)
    private LocalDate shiftDate;

    @Column(nullable = false)
    private int durationMinutes;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal hours;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal billRate;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal payRate;

    /** Client owes agency: billRate × hours. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal clientAmount;

    /** Agency owes caregiver: payRate × hours. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal caregiverAmount;

    /** Agency margin: clientAmount − caregiverAmount. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal agencyAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private PaymentStatus clientPaymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private PaymentStatus caregiverPaymentStatus = PaymentStatus.PENDING;

    @Column(nullable = false)
    private LocalDate payPeriodStart;

    @Column(nullable = false)
    private LocalDate payPeriodEnd;

    private Instant clientPaidAt;

    private Instant caregiverPaidAt;

    /** Set when this settlement is included on a non-void client invoice. */
    @Column(name = "client_invoice_id")
    private UUID clientInvoiceId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
