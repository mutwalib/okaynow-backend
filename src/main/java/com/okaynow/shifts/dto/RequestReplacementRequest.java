package com.okaynow.shifts.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Client/admin asks to open marketplace coverage for a day (call-out / empty slots).
 * {@code slots} is how many caregivers should be able to claim — typically part or all
 * of the remaining unfilled headcount.
 * <p>
 * Facilities must pass exactly one connected {@code agencyId} (via {@code agencyIds}
 * with a single entry) instead of posting to the public marketplace.
 */
public record RequestReplacementRequest(
        @Size(max = 500) String reason,
        /** Marketplace openings to post (1 … remaining, or 1 … filled when replacing). */
        @Min(1) @Max(50) Integer slots,
        /** Facility only: exactly one connected agency that should receive this opening. */
        @Size(min = 1, max = 1) List<UUID> agencyIds
) {
}
