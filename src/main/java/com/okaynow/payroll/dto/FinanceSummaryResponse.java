package com.okaynow.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceSummaryResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        long completedShifts,
        BigDecimal totalHours,
        BigDecimal clientBilled,
        BigDecimal clientCollected,
        BigDecimal clientPending,
        BigDecimal caregiverOwed,
        BigDecimal caregiverPaid,
        BigDecimal caregiverPending,
        BigDecimal agencyMarginAccrued,
        BigDecimal agencyMarginCollected
) {
}
