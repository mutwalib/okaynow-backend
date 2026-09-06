package com.okaynow.roster.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateRosterPayOfferRequest(
        @NotNull @DecimalMin("0.01") BigDecimal payRate,
        @Size(max = 300) String payOfferNote
) {
}
