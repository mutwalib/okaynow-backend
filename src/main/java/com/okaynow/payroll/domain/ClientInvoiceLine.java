package com.okaynow.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "client_invoice_lines", uniqueConstraints = {
        @UniqueConstraint(name = "uk_client_invoice_lines_settlement", columnNames = "settlement_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientInvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private ClientInvoice invoice;

    @Column(name = "settlement_id")
    private UUID settlementId;

    @Column(name = "shift_id")
    private UUID shiftId;

    /**
     * Set for platform-conversion fee lines so a client/facility cannot be
     * invoiced twice for the same caregiver.
     */
    @Column(name = "caregiver_profile_id")
    private UUID caregiverProfileId;

    @Column(nullable = false)
    private LocalDate shiftDate;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal hours;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal billRate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
}
