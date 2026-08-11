package com.okaynow.marketplace.service;

import com.okaynow.marketplace.domain.QualificationRulePack;
import com.okaynow.notifications.domain.NotificationType;
import com.okaynow.notifications.service.ShiftEventPublisher;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Facility fill escalation: radius expand + surge bonus as start time approaches.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FacilityEscalationService {

    private final ShiftRepository shiftRepository;
    private final QualificationRulePackService rulePackService;
    private final DriveTimeService driveTimeService;
    private final ShiftEventPublisher shiftEventPublisher;

    @Transactional
    public int processOpenFacilityShifts() {
        LocalDate from = LocalDate.now(com.okaynow.evv.support.ShiftWindows.ZONE);
        LocalDate to = from.plusDays(2);
        List<Shift> candidates = shiftRepository.findOpenFacilityMarketplaceBetween(from, to);
        int updated = 0;
        for (Shift shift : candidates) {
            if (applyEscalation(shift)) {
                updated++;
            }
        }
        return updated;
    }

    @Transactional
    public boolean applyEscalation(Shift shift) {
        if (shift.getStatus() != ShiftStatus.OPEN
                || !shift.isMarketplacePosted()
                || shift.getMarketplaceSlots() < 1
                || shift.getFacilityProfileId() == null) {
            return false;
        }

        QualificationRulePack pack = rulePackService.getOrCreate(shift.getRequiredQualification());
        if (!pack.isSurgeEligible()) {
            return false;
        }

        long hoursUntil = driveTimeService.hoursUntilStart(shift);
        if (hoursUntil < 0) {
            return false;
        }

        int desiredTier = 0;
        BigDecimal desiredBonus = BigDecimal.ZERO;
        int desiredRadius = 0;

        if (hoursUntil <= pack.getEscalationTier3Hours()) {
            desiredTier = 3;
            desiredBonus = pack.getEscalationTier3SurgeBonus();
            desiredRadius = pack.getEscalationTier3RadiusBonusMiles();
        } else if (hoursUntil <= pack.getEscalationTier2Hours()) {
            desiredTier = 2;
            desiredBonus = pack.getEscalationTier2SurgeBonus();
            desiredRadius = pack.getEscalationTier2RadiusBonusMiles();
        } else if (hoursUntil <= pack.getEscalationTier1Hours()) {
            desiredTier = 1;
            desiredBonus = pack.getEscalationTier1SurgeBonus();
            desiredRadius = pack.getEscalationTier1RadiusBonusMiles();
        }

        if (desiredTier <= 0 || desiredTier <= shift.getSurgeTierApplied()) {
            return false;
        }

        BigDecimal previousBonus = shift.getSurgeBonusPay() != null
                ? shift.getSurgeBonusPay()
                : BigDecimal.ZERO;
        BigDecimal delta = desiredBonus.subtract(previousBonus).setScale(2, RoundingMode.HALF_UP);
        if (delta.signum() > 0 && shift.getBillRate() != null) {
            // Facility bill side funds the surge so agency margin is preserved.
            shift.setBillRate(shift.getBillRate().add(delta));
        }
        shift.setSurgeBonusPay(desiredBonus);
        shift.setSurgeTierApplied(desiredTier);
        shift.setEscalationRadiusBonusMiles(desiredRadius);
        shiftRepository.save(shift);

        String title = desiredTier >= 3
                ? "Urgent: facility shift still open"
                : "Surge bonus applied";
        String body = "Facility " + shift.getRequiredQualification() + " shift on "
                + shift.getDate() + " in " + shift.getCity()
                + " escalated to tier " + desiredTier
                + " (+$" + desiredBonus + "/hr surge, +" + desiredRadius + " mi radius).";

        shiftEventPublisher.publish(
                desiredTier >= 3
                        ? NotificationType.SHIFT_ESCALATION_ALERT
                        : NotificationType.SHIFT_SURGE_APPLIED,
                shift,
                null,
                title,
                body);

        log.info("Escalated shift {} to surge tier {} (+{})",
                shift.getId(), desiredTier, desiredBonus);
        return true;
    }
}
