package com.okaynow.payroll.domain;

import com.okaynow.agencies.domain.ShiftRoutingMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.UUID;

/**
 * Agency economics: take % of client bill rate, and pay-period boundaries.
 * Clients enter caregiver pay; bill = pay / (1 - take%). Example: pay $22, take 35% → bill ≈ $33.85.
 *
 * <p>Legacy installs use {@link #SINGLETON_ID} with {@code agencyId == null} (platform default).
 * Multi-tenant agencies each have a row keyed by {@link #agencyId}.
 */
@Entity
@Table(name = "agency_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencySettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    /**
     * Owning tenant. Null means the legacy platform-wide singleton used by pre-tenant
     * marketplace flows and admin rate overrides.
     */
    @Column(unique = true)
    private UUID agencyId;

    /** Percent of client bill rate retained by the agency (0–99.99). */
    @Column(nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal agencyTakePercent = new BigDecimal("35.00");

    /**
     * Caregiver pay rate applied to all client-posted shifts.
     * Bill rate is always derived via {@link #billRateFromPayRate}.
     */
    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal defaultPayRate = new BigDecimal("22.00");

    /**
     * Legacy column; migrated into {@link #defaultPayRate} when present.
     * Kept so existing DBs do not lose the old value on first boot.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal defaultBillRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private PayPeriodType payPeriodType = PayPeriodType.WEEKLY;

    /** First day of each pay period (e.g. MONDAY). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private DayOfWeek periodStartDay = DayOfWeek.MONDAY;

    /** When true, completing a shift auto-creates a client invoice for the settlement. */
    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean autoInvoiceOnComplete = true;

    /**
     * When true (and auto-invoice is on), the invoice is sent to the client immediately.
     * When false, a DRAFT is left for admin review.
     */
    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean autoInvoiceSendImmediately = true;

    /**
     * Flat fee charged to the client when they reject a caregiver who claimed or
     * was assigned to their shift. Zero disables the fee (rejection still allowed).
     */
    @Column(nullable = false, precision = 10, scale = 2, columnDefinition = "numeric(10,2) default 25.00")
    @Builder.Default
    private BigDecimal clientCaregiverRejectionFee = new BigDecimal("25.00");

    /**
     * Flat fee charged when a family/facility hires a caregiver they connected with
     * through OkayNow and continues care off-platform. Zero disables the fee.
     */
    @Column(nullable = false, precision = 10, scale = 2, columnDefinition = "numeric(10,2) default 500.00")
    @Builder.Default
    private BigDecimal platformConversionFee = new BigDecimal("500.00");

    /** How accepted home/facility requests are routed to caregivers. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private ShiftRoutingMode shiftRoutingMode = ShiftRoutingMode.INBOX_FIRST;

    /**
     * Max open shifts (pending or confirmed, not completed) per caregiver at once.
     * Zero means no limit.
     */
    @Column(nullable = false, columnDefinition = "integer default 3")
    @Builder.Default
    private int maxIncompleteShiftsPerCaregiver = 3;

    /** Extra minutes required between consecutive shifts at different homes. */
    @Column(nullable = false, columnDefinition = "integer default 15")
    @Builder.Default
    private int minBufferMinutesBetweenShifts = 15;

    /** Reject assignments when drive time between different homes exceeds this. Zero disables. */
    @Column(nullable = false, columnDefinition = "integer default 45")
    @Builder.Default
    private int maxDriveMinutesBetweenShifts = 45;

    public BigDecimal suggestedPayRate(BigDecimal billRate) {
        if (billRate == null) {
            return null;
        }
        BigDecimal take = agencyTakePercent
                .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        return billRate.multiply(BigDecimal.ONE.subtract(take))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Inverse of {@link #suggestedPayRate}: what the client is billed so the caregiver
     * receives {@code payRate} after agency take.
     */
    public BigDecimal billRateFromPayRate(BigDecimal payRate) {
        if (payRate == null) {
            return null;
        }
        BigDecimal take = agencyTakePercent
                .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        BigDecimal keep = BigDecimal.ONE.subtract(take);
        if (keep.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("agencyTakePercent must be less than 100 to derive bill rate");
        }
        return payRate.divide(keep, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal agencyTakePerHour(BigDecimal billRate) {
        if (billRate == null) {
            return null;
        }
        return billRate.multiply(agencyTakePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
