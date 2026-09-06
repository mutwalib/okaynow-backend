package com.okaynow.hiring.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AcceptCaregiverInterestRequest(
        @NotNull @DecimalMin("0.01") BigDecimal payRate,
        @Size(max = 300) String payOfferNote
) {
}
