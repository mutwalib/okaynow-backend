package com.okaynow.marketplace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FacilityEscalationScheduler {

    private final FacilityEscalationService facilityEscalationService;

    /** Every 5 minutes: bump surge / radius on unfilled facility seats. */
    @Scheduled(fixedDelayString = "${okaynow.marketplace.escalation-interval-ms:300000}")
    public void tick() {
        int n = facilityEscalationService.processOpenFacilityShifts();
        if (n > 0) {
            log.info("Facility escalation updated {} shift(s)", n);
        }
    }
}
