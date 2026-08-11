package com.okaynow.staffing.dto;

import com.okaynow.staffing.domain.AssignmentType;
import com.okaynow.users.domain.Qualification;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Caregiver ranked for a shift by continuity (roster + history), not gig randomness.
 */
public record ContinuityCaregiverSuggestion(
        UUID caregiverProfileId,
        String firstName,
        String lastName,
        String email,
        Set<Qualification> qualifications,
        int continuityScore,
        String continuityLabel,
        AssignmentType rosterType,
        long completedShiftsWithClient,
        BigDecimal ratingAvg,
        boolean eligible
) {
}
