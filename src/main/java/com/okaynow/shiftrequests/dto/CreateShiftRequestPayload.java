package com.okaynow.shiftrequests.dto;

import com.okaynow.users.domain.Qualification;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CreateShiftRequestPayload(
        @NotNull Qualification requiredQualification,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        String addressLine,
        String city,
        @Size(max = 2) String state,
        String zip,
        @Size(max = 2000) String notes,
        @NotEmpty List<UUID> agencyIds
) {
}
