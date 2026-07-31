package com.okaynow.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record ExtendShiftRequest(@NotNull LocalTime endTime) {
}
