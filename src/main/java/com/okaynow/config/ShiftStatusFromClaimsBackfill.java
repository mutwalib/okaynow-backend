package com.okaynow.config;

import com.okaynow.booking.domain.ClaimSource;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.booking.repository.ShiftClaimRepository;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

/**
 * Repair shifts incorrectly left as DRAFT/CLAIMED after a partial marketplace fill
 * when remaining seats should stay OPEN on the board.
 */
@Component
@Order(50)
@RequiredArgsConstructor
@Slf4j
public class ShiftStatusFromClaimsBackfill implements ApplicationRunner {

    private static final EnumSet<ShiftClaimStatus> ACTIVE = EnumSet.of(
            ShiftClaimStatus.PENDING, ShiftClaimStatus.CONFIRMED);

    private final ShiftRepository shiftRepository;
    private final ShiftClaimRepository shiftClaimRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updated = 0;
        for (Shift shift : shiftRepository.findAll()) {
            ShiftStatus before = shift.getStatus();
            if (before == ShiftStatus.CANCELLED
                    || before == ShiftStatus.COMPLETED
                    || before == ShiftStatus.IN_PROGRESS
                    || before == ShiftStatus.NO_SHOW) {
                continue;
            }

            int required = Math.max(1, shift.getRequiredHeadcount());
            var claims = shiftClaimRepository.findByShiftIdOrderByClaimedAtDesc(shift.getId());
            int filled = (int) claims.stream()
                    .filter(c -> ACTIVE.contains(c.getStatus()))
                    .count();
            int confirmed = (int) claims.stream()
                    .filter(c -> c.getStatus() == ShiftClaimStatus.CONFIRMED)
                    .count();
            boolean marketplaceClaim = claims.stream()
                    .anyMatch(c -> ACTIVE.contains(c.getStatus())
                            && c.getSource() == ClaimSource.MARKETPLACE);

            boolean wasPosted = shift.isMarketplacePosted();
            int wasSlots = shift.getMarketplaceSlots();
            int wasFilled = shift.getFilledSlots();

            shift.setFilledSlots(filled);
            shift.setRequiredHeadcount(required);

            ShiftStatus next = before;
            if (filled >= required) {
                shift.setMarketplacePosted(false);
                shift.setMarketplaceSlots(0);
                next = confirmed >= required ? ShiftStatus.CONFIRMED : ShiftStatus.CLAIMED;
            } else if (wasPosted || wasSlots > 0 || marketplaceClaim) {
                // Remaining headcount stays claimable on the marketplace.
                shift.setMarketplacePosted(true);
                shift.setMarketplaceSlots(required - filled);
                next = ShiftStatus.OPEN;
            } else if (filled > 0) {
                shift.setMarketplacePosted(false);
                shift.setMarketplaceSlots(0);
                next = ShiftStatus.CLAIMED;
            }

            boolean changed = next != before
                    || wasFilled != filled
                    || wasPosted != shift.isMarketplacePosted()
                    || wasSlots != shift.getMarketplaceSlots();
            if (changed) {
                shift.setStatus(next);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Repaired status on {} shifts from active claims", updated);
        }
    }
}
