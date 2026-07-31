package com.okaynow.payroll.dto;

import jakarta.validation.constraints.NotNull;

import com.okaynow.payroll.domain.PaymentStatus;

public record UpdatePaymentStatusRequest(
        @NotNull PaymentStatus status
) {
}
