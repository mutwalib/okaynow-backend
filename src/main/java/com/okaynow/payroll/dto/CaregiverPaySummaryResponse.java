package com.okaynow.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CaregiverPaySummaryResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        long shiftCount,
        BigDecimal totalHours,
        BigDecimal totalEarned,
        BigDecimal paid,
        BigDecimal pending
) {
}
