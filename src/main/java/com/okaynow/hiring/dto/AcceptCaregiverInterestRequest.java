package com.okaynow.hiring.dto;

import com.okaynow.roster.domain.RosterPayClassification;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AcceptCaregiverInterestRequest(
        @NotNull @DecimalMin("0.01") BigDecimal payRate,
        @NotNull RosterPayClassification payClassification
) {
}
