package com.okaynow.payroll.dto;

import com.okaynow.payroll.domain.PayPeriodType;

import java.math.BigDecimal;
import java.time.DayOfWeek;

public record AgencySettingsResponse(
        BigDecimal agencyTakePercent,
        BigDecimal defaultPayRate,
        PayPeriodType payPeriodType,
        DayOfWeek periodStartDay,
        boolean autoInvoiceOnComplete,
        boolean autoInvoiceSendImmediately,
        BigDecimal clientCaregiverRejectionFee,
        BigDecimal platformConversionFee
) {
}
