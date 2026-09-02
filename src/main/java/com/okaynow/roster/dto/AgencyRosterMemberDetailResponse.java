package com.okaynow.roster.dto;

import com.okaynow.roster.domain.AgencyCaregiverStatus;
import com.okaynow.users.domain.Qualification;
import com.okaynow.users.domain.UserStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full caregiver dossier for an agency roster member. */
public record AgencyRosterMemberDetailResponse(
        UUID rosterId,
        AgencyCaregiverStatus rosterStatus,
        String inviteMessage,
        Instant invitedAt,
        Instant respondedAt,
        Instant removedAt,
        UUID caregiverProfileId,
        UUID caregiverUserId,
        String firstName,
        String lastName,
        String email,
        String phone,
        UserStatus accountStatus,
        List<Qualification> qualifications,
        String otherQualificationDetail,
        BigDecimal hourlyRateMin,
        BigDecimal hourlyRateMax,
        Integer serviceRadiusMiles,
        String homeAddressLine,
        String homeCity,
        String homeState,
        String homeZip,
        String profilePhotoUrl,
        String cvUrl,
        Instant cvUploadedAt,
        BigDecimal ratingAvg,
        Integer ratingCount
) {
}
