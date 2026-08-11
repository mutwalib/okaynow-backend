package com.okaynow.marketplace.dto;

import com.okaynow.marketplace.domain.CredentialType;
import com.okaynow.marketplace.domain.MatchingMode;
import com.okaynow.marketplace.domain.ShiftChannel;
import com.okaynow.users.domain.Qualification;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record QualificationRulePackResponse(
        UUID id,
        Qualification qualification,
        ShiftChannel preferredChannel,
        MatchingMode matchingMode,
        boolean enforceCredentials,
        Set<CredentialType> requiredCredentials,
        int credentialExpiryBlockDays,
        int cancelNoticeHours,
        boolean surgeEligible,
        boolean evvRequired,
        Integer maxDriveMinutes,
        boolean travelPayEnabled,
        BigDecimal travelPayPerMinute,
        int escalationTier1Hours,
        BigDecimal escalationTier1SurgeBonus,
        int escalationTier1RadiusBonusMiles,
        int escalationTier2Hours,
        BigDecimal escalationTier2SurgeBonus,
        int escalationTier2RadiusBonusMiles,
        int escalationTier3Hours,
        BigDecimal escalationTier3SurgeBonus,
        int escalationTier3RadiusBonusMiles
) {
}
