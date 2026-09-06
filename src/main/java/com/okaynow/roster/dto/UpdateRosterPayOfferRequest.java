package com.okaynow.roster.dto;

import com.okaynow.roster.domain.RosterPayClassification;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateRosterPayOfferRequest(
        @NotNull @DecimalMin("0.01") BigDecimal payRate,
        @NotNull RosterPayClassification payClassification
) {
}
