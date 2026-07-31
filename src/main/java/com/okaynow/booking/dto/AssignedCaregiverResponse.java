package com.okaynow.booking.dto;

import com.okaynow.booking.domain.ClaimSource;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.users.domain.Qualification;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Client/facility view of who is coming — name and essentials only
 * (no email, address, rates, or credential documents).
 */
public record AssignedCaregiverResponse(
        UUID claimId,
        UUID caregiverProfileId,
        String firstName,
        String lastName,
        String phone,
        Set<Qualification> qualifications,
        BigDecimal ratingAvg,
        Integer ratingCount,
        String profilePhotoUrl,
        ShiftClaimStatus status,
        ClaimSource source
) {
}
