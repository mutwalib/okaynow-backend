package com.okaynow.shifts.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Client/admin asks to open marketplace coverage for a day (call-out / empty slots).
 * {@code slots} is how many caregivers should be able to claim — typically part or all
 * of the remaining unfilled headcount.
 */
public record RequestReplacementRequest(
        @Size(max = 500) String reason,
        /** Marketplace openings to post (1 … remaining, or 1 … filled when replacing). */
        @Min(1) @Max(50) Integer slots
) {
}
