package com.okaynow.shifts.dto;

import com.okaynow.users.domain.Qualification;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Partial update: null fields are left unchanged. Deliberately excludes status —
 * lifecycle transitions go through claim/release/confirm and the admin
 * start/complete endpoints, never through a generic PATCH.
 */
public record UpdateShiftRequest(
        Qualification requiredQualification,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String addressLine,
        String city,
        @Size(max = 2) String state,
        String zip,
        Double lat,
        Double lng,
        @DecimalMin("0.01") BigDecimal payRate,
        @DecimalMin("0.01") BigDecimal billRate,
        @Size(max = 2000) String notes
) {
}
