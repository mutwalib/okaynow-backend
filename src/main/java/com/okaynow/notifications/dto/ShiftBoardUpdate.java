package com.okaynow.notifications.dto;

import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.users.domain.Qualification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Broadcast on {@code /topic/shifts} so boards can refresh live. */
public record ShiftBoardUpdate(
        String action,
        UUID shiftId,
        ShiftStatus status,
        UUID clientProfileId,
        String city,
        LocalDate date,
        Qualification requiredQualification,
        BigDecimal payRate,
        Integer marketplaceSlots,
        Instant at
) {
}
