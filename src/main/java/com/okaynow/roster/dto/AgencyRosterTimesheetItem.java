package com.okaynow.roster.dto;

import com.okaynow.payroll.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AgencyRosterTimesheetItem(
        UUID settlementId,
        UUID shiftId,
        LocalDate shiftDate,
        BigDecimal hours,
        BigDecimal payRate,
        BigDecimal caregiverAmount,
        PaymentStatus caregiverPaymentStatus,
        PaymentStatus clientPaymentStatus,
        LocalDate payPeriodStart,
        LocalDate payPeriodEnd,
        Instant caregiverPaidAt,
        String callToAction
) {
}
