package com.okaynow.payroll.dto;

import com.okaynow.payroll.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Caregiver-facing pay line — no client bill or agency margin. */
public record CaregiverPayEntryResponse(
        UUID id,
        UUID shiftId,
        LocalDate shiftDate,
        LocalTime startTime,
        LocalTime endTime,
        /** True when end time is on the calendar day after shiftDate (e.g. 11 PM – 3 AM). */
        boolean endsNextDay,
        String clientFirstName,
        String clientLastName,
        BigDecimal hours,
        BigDecimal payRate,
        BigDecimal amount,
        PaymentStatus paymentStatus,
        LocalDate payPeriodStart,
        LocalDate payPeriodEnd,
        Instant paidAt,
        UUID agencyId,
        String agencyDisplayName
) {
}
