package com.okaynow.marketplace.dto;

import com.okaynow.marketplace.domain.CredentialType;
import com.okaynow.marketplace.domain.MatchingMode;
import com.okaynow.marketplace.domain.ShiftChannel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Set;

public record UpdateQualificationRulePackRequest(
        @NotNull ShiftChannel preferredChannel,
        @NotNull MatchingMode matchingMode,
        boolean enforceCredentials,
        Set<CredentialType> requiredCredentials,
        @Min(0) @Max(365) int credentialExpiryBlockDays,
        @Min(0) @Max(168) int cancelNoticeHours,
        boolean surgeEligible,
        boolean evvRequired,
        @Min(1) @Max(300) Integer maxDriveMinutes,
        boolean travelPayEnabled,
        @DecimalMin("0.00") BigDecimal travelPayPerMinute,
        @Min(0) @Max(72) int escalationTier1Hours,
        @DecimalMin("0.00") BigDecimal escalationTier1SurgeBonus,
        @Min(0) @Max(100) int escalationTier1RadiusBonusMiles,
        @Min(0) @Max(72) int escalationTier2Hours,
        @DecimalMin("0.00") BigDecimal escalationTier2SurgeBonus,
        @Min(0) @Max(100) int escalationTier2RadiusBonusMiles,
        @Min(0) @Max(72) int escalationTier3Hours,
        @DecimalMin("0.00") BigDecimal escalationTier3SurgeBonus,
        @Min(0) @Max(100) int escalationTier3RadiusBonusMiles
) {
}
