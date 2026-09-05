package com.okaynow.payroll.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Per-agency (or independent) slice of a caregiver pay summary. */
public record CaregiverAgencyPaySlice(
        /** Null means independent marketplace / non-agency shifts. */
        UUID agencyId,
        String agencyDisplayName,
        long shiftCount,
        BigDecimal totalHours,
        BigDecimal totalEarned,
        BigDecimal paid,
        BigDecimal pending
) {
}
