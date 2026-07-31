package com.okaynow.payroll.dto;

import com.okaynow.payroll.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Full settlement for admin/agency views (includes client/facility bill side). */
public record SettlementResponse(
        UUID id,
        UUID shiftId,
        UUID claimId,
        UUID caregiverProfileId,
        String caregiverFirstName,
        String caregiverLastName,
        UUID clientProfileId,
        String clientFirstName,
        String clientLastName,
        UUID facilityProfileId,
        String facilityName,
        LocalDate shiftDate,
        int durationMinutes,
        BigDecimal hours,
        BigDecimal billRate,
        BigDecimal payRate,
        BigDecimal clientAmount,
        BigDecimal caregiverAmount,
        BigDecimal agencyAmount,
        PaymentStatus clientPaymentStatus,
        PaymentStatus caregiverPaymentStatus,
        LocalDate payPeriodStart,
        LocalDate payPeriodEnd,
        Instant clientPaidAt,
        Instant caregiverPaidAt,
        UUID clientInvoiceId
) {
}
