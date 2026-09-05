package com.okaynow.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PastShiftExpiryScheduler {

    private final PastShiftExpiryService pastShiftExpiryService;

    /** Mark past unfinished assignments expired so they leave the open-shift cap. */
    @Scheduled(fixedDelayString = "${okaynow.shifts.expiry-interval-ms:300000}")
    public void expirePastShifts() {
        try {
            pastShiftExpiryService.expireDueShifts();
        } catch (Exception ex) {
            log.warn("Past shift expiry sweep failed: {}", ex.getMessage());
        }
    }
}
