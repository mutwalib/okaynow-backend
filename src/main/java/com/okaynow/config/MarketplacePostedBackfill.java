package com.okaynow.config;

import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

/**
 * Existing rows that already left DRAFT were marketplace-visible before
 * {@code marketplacePosted} existed — mark them so cancel/reopen stays correct.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MarketplacePostedBackfill implements ApplicationRunner {

    private final ShiftRepository shiftRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var statuses = EnumSet.of(
                ShiftStatus.OPEN,
                ShiftStatus.CLAIMED,
                ShiftStatus.CONFIRMED,
                ShiftStatus.IN_PROGRESS,
                ShiftStatus.COMPLETED);
        int updated = 0;
        for (var shift : shiftRepository.findAll()) {
            if (!shift.isMarketplacePosted() && statuses.contains(shift.getStatus())) {
                shift.setMarketplacePosted(true);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Backfilled marketplacePosted=true on {} existing shifts", updated);
        }
    }
}
