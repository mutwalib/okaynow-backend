package com.okaynow.roster.dto;

import com.okaynow.roster.domain.RosterPayClassification;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record InviteRosterCaregiverRequest(
        @Email String email,
        @NotNull @DecimalMin("0.01") BigDecimal payRate,
        @NotNull RosterPayClassification payClassification,
        @Size(max = 500) String message
) {
}
