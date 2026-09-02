package com.okaynow.payroll.dto;

import com.okaynow.agencies.domain.ShiftRoutingMode;
import com.okaynow.payroll.domain.PayPeriodType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.DayOfWeek;

public record UpdateAgencySettingsRequest(
        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("99.99")
        BigDecimal agencyTakePercent,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal defaultPayRate,

        @NotNull
        PayPeriodType payPeriodType,

        @NotNull
        DayOfWeek periodStartDay,

        @NotNull
        Boolean autoInvoiceOnComplete,

        @NotNull
        Boolean autoInvoiceSendImmediately,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal clientCaregiverRejectionFee,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal platformConversionFee,

        @NotNull
        ShiftRoutingMode shiftRoutingMode,

        @Min(0)
        @Max(50)
        int maxIncompleteShiftsPerCaregiver,

        @Min(0)
        @Max(240)
        int minBufferMinutesBetweenShifts,

        @Min(0)
        @Max(240)
        int maxDriveMinutesBetweenShifts
) {
}
