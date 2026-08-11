package com.okaynow.payroll.service;

import com.okaynow.audit.domain.AuditAction;
import com.okaynow.audit.service.AuditLogService;
import com.okaynow.booking.domain.ShiftClaim;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.booking.repository.ShiftClaimRepository;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.payroll.domain.AgencySettings;
import com.okaynow.payroll.domain.PaymentStatus;
import com.okaynow.payroll.domain.ShiftSettlement;
import com.okaynow.payroll.dto.SettlementResponse;
import com.okaynow.payroll.repository.ShiftSettlementRepository;
import com.okaynow.payroll.support.PayPeriodCalculator;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.FacilityProfile;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.FacilityProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private static final EnumSet<ShiftClaimStatus> COMPLETED_CLAIM =
            EnumSet.of(ShiftClaimStatus.COMPLETED, ShiftClaimStatus.CONFIRMED, ShiftClaimStatus.PENDING);

    private final ShiftSettlementRepository settlementRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftClaimRepository shiftClaimRepository;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final AgencySettingsService agencySettingsService;
    private final AuditLogService auditLogService;

    /**
     * Creates a settlement when a shift completes (idempotent).
     */
    @Transactional
    public Optional<ShiftSettlement> createForCompletedShift(UUID shiftId) {
        if (settlementRepository.existsByShiftId(shiftId)) {
            return settlementRepository.findByShiftId(shiftId);
        }
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        if (shift.getStatus() != ShiftStatus.COMPLETED) {
            return Optional.empty();
        }
        ShiftClaim claim = shiftClaimRepository
                .findFirstByShiftIdAndStatusIn(shiftId, COMPLETED_CLAIM)
                .orElse(null);
        if (claim == null || claim.getCaregiverProfile() == null) {
            return Optional.empty();
        }
        return Optional.of(buildAndSave(shift, claim));
    }

    /**
     * Removes the settlement for a shift that is being un-completed.
     * Refuses if client or caregiver payment was already marked paid.
     */
    @Transactional
    public void deleteIfUnpaidForShift(UUID shiftId) {
        Optional<ShiftSettlement> opt = settlementRepository.findByShiftId(shiftId);
        if (opt.isEmpty()) {
            return;
        }
        ShiftSettlement settlement = opt.get();
        if (settlement.getClientPaymentStatus() == PaymentStatus.PAID
                || settlement.getCaregiverPaymentStatus() == PaymentStatus.PAID) {
            throw new BadRequestException(
                    "Cannot undo completion: settlement already has paid client or caregiver amounts. "
                            + "Mark those unpaid in Finance first.");
        }
        if (settlement.getClientInvoiceId() != null) {
            throw new BadRequestException(
                    "Cannot undo completion: settlement is on a client invoice. Void the invoice first.");
        }
        settlementRepository.delete(settlement);
    }

    @Transactional
    public ShiftSettlement ensureForShift(UUID shiftId) {
        return settlementRepository.findByShiftId(shiftId)
                .orElseGet(() -> createForCompletedShift(shiftId)
                        .orElseThrow(() -> new BadRequestException(
                                "Settlement requires a completed shift with an assigned caregiver")));
    }

    @Transactional
    public void syncFromPlatformPaid(Shift shift, boolean platformPaid, User actor) {
        if (shift.getStatus() != ShiftStatus.COMPLETED) {
            return;
        }
        ShiftSettlement settlement = ensureForShift(shift.getId());
        PaymentStatus status = platformPaid ? PaymentStatus.PAID : PaymentStatus.PENDING;
        applyClientStatus(settlement, status);
        applyCaregiverStatus(settlement, status);
        settlementRepository.save(settlement);
        auditLogService.record(actor, AuditAction.SETTLEMENT_PAYMENT_SYNCED, "SETTLEMENT",
                settlement.getId(), shift.getClientProfileId(),
                "platformPaid=" + platformPaid);
    }

    @Transactional
    public SettlementResponse markClientPayment(UUID settlementId, PaymentStatus status, User actor) {
        ShiftSettlement settlement = find(settlementId);
        applyClientStatus(settlement, status);
        syncPlatformPaidFlag(settlement);
        settlementRepository.save(settlement);
        auditLogService.record(actor, AuditAction.CLIENT_PAYMENT_UPDATED, "SETTLEMENT",
                settlement.getId(), settlement.getClientProfileId(),
                "clientPaymentStatus=" + status);
        return toAdminResponse(settlement);
    }

    @Transactional
    public SettlementResponse markCaregiverPayment(UUID settlementId, PaymentStatus status, User actor) {
        ShiftSettlement settlement = find(settlementId);
        applyCaregiverStatus(settlement, status);
        syncPlatformPaidFlag(settlement);
        settlementRepository.save(settlement);
        auditLogService.record(actor, AuditAction.CAREGIVER_PAYMENT_UPDATED, "SETTLEMENT",
                settlement.getId(), settlement.getClientProfileId(),
                "caregiverPaymentStatus=" + status);
        return toAdminResponse(settlement);
    }

    @Transactional(readOnly = true)
    public SettlementResponse getByShiftId(UUID shiftId) {
        return settlementRepository.findByShiftId(shiftId)
                .map(this::toAdminResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement not found for shift"));
    }

    /** Ensures every completed shift in [start, end] has a settlement row. */
    @Transactional
    public void backfillCompletedInPeriod(LocalDate start, LocalDate end) {
        shiftRepository.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("status"), ShiftStatus.COMPLETED),
                cb.greaterThanOrEqualTo(root.get("date"), start),
                cb.lessThanOrEqualTo(root.get("date"), end)
        )).forEach(shift -> createForCompletedShift(shift.getId()));
    }

    public SettlementResponse toAdminResponse(ShiftSettlement s) {
        CaregiverProfile cg = caregiverProfileRepository.findById(s.getCaregiverProfileId()).orElse(null);
        ClientProfile client = s.getClientProfileId() != null
                ? clientProfileRepository.findById(s.getClientProfileId()).orElse(null)
                : null;
        FacilityProfile facility = s.getFacilityProfileId() != null
                ? facilityProfileRepository.findById(s.getFacilityProfileId()).orElse(null)
                : null;
        return new SettlementResponse(
                s.getId(),
                s.getShiftId(),
                s.getClaimId(),
                s.getCaregiverProfileId(),
                cg != null ? cg.getFirstName() : null,
                cg != null ? cg.getLastName() : null,
                s.getClientProfileId(),
                client != null ? client.getFirstName() : null,
                client != null ? client.getLastName() : null,
                s.getFacilityProfileId(),
                facility != null ? facility.getFacilityName() : null,
                s.getShiftDate(),
                s.getDurationMinutes(),
                s.getHours(),
                s.getBillRate(),
                s.getPayRate(),
                s.getClientAmount(),
                s.getCaregiverAmount(),
                s.getAgencyAmount(),
                s.getClientPaymentStatus(),
                s.getCaregiverPaymentStatus(),
                s.getPayPeriodStart(),
                s.getPayPeriodEnd(),
                s.getClientPaidAt(),
                s.getCaregiverPaidAt(),
                s.getClientInvoiceId());
    }

    private ShiftSettlement buildAndSave(Shift shift, ShiftClaim claim) {
        AgencySettings settings = agencySettingsService.getOrCreate();
        var period = PayPeriodCalculator.forDate(shift.getDate(), settings);

        int minutes = shift.getDurationMinutes();
        if (minutes <= 0 && shift.getStartTime() != null && shift.getEndTime() != null) {
            minutes = com.okaynow.evv.support.ShiftWindows.durationMinutes(shift);
        }
        BigDecimal hours = BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        BigDecimal surge = shift.getSurgeBonusPay() != null
                ? shift.getSurgeBonusPay()
                : BigDecimal.ZERO;
        BigDecimal effectivePay = shift.getPayRate().add(surge);
        BigDecimal clientAmount = shift.getBillRate().multiply(hours).setScale(2, RoundingMode.HALF_UP);
        BigDecimal caregiverAmount = effectivePay.multiply(hours).setScale(2, RoundingMode.HALF_UP);
        if (claim.getTravelPayAmount() != null) {
            caregiverAmount = caregiverAmount.add(claim.getTravelPayAmount())
                    .setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal agencyAmount = clientAmount.subtract(caregiverAmount);

        PaymentStatus initialClient = shift.isPlatformPaid() ? PaymentStatus.PAID : PaymentStatus.PENDING;
        PaymentStatus initialCaregiver = shift.isPlatformPaid() ? PaymentStatus.PAID : PaymentStatus.PENDING;

        ShiftSettlement settlement = ShiftSettlement.builder()
                .shiftId(shift.getId())
                .claimId(claim.getId())
                .caregiverProfileId(claim.getCaregiverProfile().getId())
                .clientProfileId(shift.getClientProfileId())
                .facilityProfileId(shift.getFacilityProfileId())
                .shiftDate(shift.getDate())
                .durationMinutes(minutes)
                .hours(hours)
                .billRate(shift.getBillRate())
                .payRate(effectivePay)
                .clientAmount(clientAmount)
                .caregiverAmount(caregiverAmount)
                .agencyAmount(agencyAmount)
                .clientPaymentStatus(initialClient)
                .caregiverPaymentStatus(initialCaregiver)
                .payPeriodStart(period.start())
                .payPeriodEnd(period.end())
                .clientPaidAt(initialClient == PaymentStatus.PAID ? Instant.now() : null)
                .caregiverPaidAt(initialCaregiver == PaymentStatus.PAID ? Instant.now() : null)
                .build();
        return settlementRepository.save(settlement);
    }

    private void applyClientStatus(ShiftSettlement settlement, PaymentStatus status) {
        settlement.setClientPaymentStatus(status);
        settlement.setClientPaidAt(status == PaymentStatus.PAID ? Instant.now() : null);
    }

    private void applyCaregiverStatus(ShiftSettlement settlement, PaymentStatus status) {
        settlement.setCaregiverPaymentStatus(status);
        settlement.setCaregiverPaidAt(status == PaymentStatus.PAID ? Instant.now() : null);
    }

    private void syncPlatformPaidFlag(ShiftSettlement settlement) {
        boolean bothPaid = settlement.getClientPaymentStatus() == PaymentStatus.PAID
                && settlement.getCaregiverPaymentStatus() == PaymentStatus.PAID;
        shiftRepository.findById(settlement.getShiftId()).ifPresent(shift -> {
            shift.setPlatformPaid(bothPaid);
        });
    }

    private ShiftSettlement find(UUID id) {
        return settlementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement not found"));
    }
}
