package com.okaynow.config;

import com.okaynow.payroll.domain.ShiftSettlement;
import com.okaynow.payroll.repository.ShiftSettlementRepository;
import com.okaynow.shifts.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Backfill facility bill-to on settlements created before facilityProfileId existed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FacilitySettlementBillToBackfill implements ApplicationRunner {

    private final ShiftSettlementRepository settlementRepository;
    private final ShiftRepository shiftRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<ShiftSettlement> missing = settlementRepository.findMissingFacilityBillTo();
        if (missing.isEmpty()) {
            return;
        }
        List<ShiftSettlement> updated = new ArrayList<>();
        for (ShiftSettlement settlement : missing) {
            shiftRepository.findById(settlement.getShiftId()).ifPresent(shift -> {
                if (shift.getFacilityProfileId() != null) {
                    settlement.setFacilityProfileId(shift.getFacilityProfileId());
                    updated.add(settlement);
                }
            });
        }
        if (!updated.isEmpty()) {
            settlementRepository.saveAll(updated);
            log.info("Backfilled facilityProfileId on {} settlements", updated.size());
        }
    }
}
