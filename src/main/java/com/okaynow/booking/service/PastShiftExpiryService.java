package com.okaynow.booking.service;

import com.okaynow.booking.domain.ShiftClaim;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.booking.repository.ShiftClaimRepository;
import com.okaynow.evv.repository.VisitRepository;
import com.okaynow.evv.support.ShiftWindows;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Marks past unfinished claims/shifts as {@link ShiftClaimStatus#EXPIRED} /
 * {@link ShiftStatus#EXPIRED} so they leave the incomplete-shift cap and upcoming lists.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PastShiftExpiryService {

    private static final EnumSet<ShiftClaimStatus> OPEN_CLAIMS =
            EnumSet.of(ShiftClaimStatus.PENDING, ShiftClaimStatus.CONFIRMED);
    private static final EnumSet<ShiftStatus> TERMINAL_SHIFTS = EnumSet.of(
            ShiftStatus.COMPLETED,
            ShiftStatus.CANCELLED,
            ShiftStatus.NO_SHOW,
            ShiftStatus.EXPIRED);

    private final ShiftClaimRepository shiftClaimRepository;
    private final ShiftRepository shiftRepository;
    private final VisitRepository visitRepository;

    /** Expire all due open claims (scheduled + opportunistic). */
    @Transactional
    public int expireDueShifts() {
        LocalDate today = LocalDate.now(ShiftWindows.ZONE);
        // Wide lookback so older unfinished claims are cleaned up after deploy.
        LocalDate fromDate = today.minusDays(365);
        List<ShiftClaim> candidates = shiftClaimRepository.findOpenClaimsPossiblyPast(
                OPEN_CLAIMS, fromDate, today);
        Instant now = Instant.now();
        Set<UUID> touchedShifts = new HashSet<>();
        int expiredClaims = 0;
        for (ShiftClaim claim : candidates) {
            if (expireClaimIfPast(claim, now)) {
                expiredClaims++;
                touchedShifts.add(claim.getShift().getId());
            }
        }
        for (UUID shiftId : touchedShifts) {
            finalizeShiftIfPast(shiftId, now);
        }
        if (expiredClaims > 0) {
            log.info("Expired {} past open claim(s) across {} shift(s)",
                    expiredClaims, touchedShifts.size());
        }
        return expiredClaims;
    }

    /** Opportunistic single-claim expiry (e.g. when listing my claims). */
    @Transactional
    public ShiftClaim expireClaimIfPast(ShiftClaim claim) {
        if (claim == null) {
            return null;
        }
        Instant now = Instant.now();
        if (expireClaimIfPast(claim, now)) {
            finalizeShiftIfPast(claim.getShift().getId(), now);
        }
        return claim;
    }

    private boolean expireClaimIfPast(ShiftClaim claim, Instant now) {
        if (claim.getStatus() != ShiftClaimStatus.PENDING
                && claim.getStatus() != ShiftClaimStatus.CONFIRMED) {
            return false;
        }
        Shift shift = claim.getShift();
        if (shift == null || TERMINAL_SHIFTS.contains(shift.getStatus())) {
            return false;
        }
        // Still in the scheduled window (including overnight).
        if (!ShiftWindows.endInstant(shift).isBefore(now)) {
            return false;
        }
        // Caregiver already clocked in — leave for normal complete / attendance flow.
        if (visitRepository.existsByShiftId(shift.getId())) {
            return false;
        }
        claim.setStatus(ShiftClaimStatus.EXPIRED);
        claim.setCancelReason("Shift window ended");
        shiftClaimRepository.save(claim);
        return true;
    }

    private void finalizeShiftIfPast(UUID shiftId, Instant now) {
        Shift shift = shiftRepository.findById(shiftId).orElse(null);
        if (shift == null || TERMINAL_SHIFTS.contains(shift.getStatus())) {
            return;
        }
        if (!ShiftWindows.endInstant(shift).isBefore(now)) {
            return;
        }
        if (visitRepository.existsByShiftId(shiftId)) {
            return;
        }
        if (shift.getStatus() == ShiftStatus.IN_PROGRESS) {
            return;
        }
        long stillOpen = shiftClaimRepository.countByShiftIdAndStatusIn(shiftId, OPEN_CLAIMS);
        if (stillOpen > 0) {
            return;
        }
        shift.setStatus(ShiftStatus.EXPIRED);
        shift.setMarketplacePosted(false);
        shift.setMarketplaceSlots(0);
        shift.setFilledSlots(0);
        shiftRepository.save(shift);
    }
}
