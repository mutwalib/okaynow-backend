package com.okaynow.payroll.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Agency invoice demanding payment from a family client or facility for completed shift settlements.
 */
@Entity
@Table(name = "client_invoices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_client_invoices_number", columnNames = "invoice_number")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_number", nullable = false, length = 32)
    private String invoiceNumber;

    /** Owning agency tenant (null for legacy platform-only invoices). */
    @Column(name = "agency_id")
    private UUID agencyId;

    /** Family client bill-to (null when invoicing a facility). */
    @Column(name = "client_profile_id")
    private UUID clientProfileId;

    /** Facility bill-to (null when invoicing a family client). */
    @Column(name = "facility_profile_id")
    private UUID facilityProfileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(nullable = false)
    private LocalDate issuedDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    private Instant sentAt;

    private Instant paidAt;

    private Instant voidedAt;

    /** Stripe Checkout Session id for Connect payment collection. */
    @Column(length = 128)
    private String stripeCheckoutSessionId;

    /** Stripe PaymentIntent id after successful Connect checkout. */
    @Column(length = 128)
    private String stripePaymentIntentId;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("shiftDate ASC")
    @Builder.Default
    private List<ClientInvoiceLine> lines = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public void addLine(ClientInvoiceLine line) {
        lines.add(line);
        line.setInvoice(this);
    }
}
