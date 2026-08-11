package com.okaynow.marketplace.service;

import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.geo.GeoUtils;
import com.okaynow.marketplace.domain.CaregiverCredential;
import com.okaynow.marketplace.domain.CredentialType;
import com.okaynow.marketplace.domain.CredentialVerificationStatus;
import com.okaynow.marketplace.domain.MatchingMode;
import com.okaynow.marketplace.domain.QualificationRulePack;
import com.okaynow.marketplace.domain.ShiftChannel;
import com.okaynow.marketplace.repository.CaregiverCredentialRepository;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.users.domain.CaregiverProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gates claim / invite / board visibility using qualification rule packs.
 */
@Service
@RequiredArgsConstructor
public class MarketplaceEligibilityService {

    private final QualificationRulePackService rulePackService;
    private final CaregiverCredentialRepository credentialRepository;
    private final DriveTimeService driveTimeService;

    /** Marketplace claim / private invite: full pack rules including channel preference. */
    @Transactional(readOnly = true, noRollbackFor = BadRequestException.class)
    public void assertCanClaim(CaregiverProfile caregiver, Shift shift) {
        assertEligible(caregiver, shift, true);
    }

    /** Admin assign may override preferred channel (still checks quals, creds, geo). */
    @Transactional(readOnly = true, noRollbackFor = BadRequestException.class)
    public void assertCanAssign(CaregiverProfile caregiver, Shift shift) {
        assertEligible(caregiver, shift, false);
    }

    @Transactional(readOnly = true)
    public boolean isEligible(CaregiverProfile caregiver, Shift shift) {
        try {
            assertCanClaim(caregiver, shift);
            return true;
        } catch (BadRequestException ex) {
            return false;
        }
    }

    private void assertEligible(
            CaregiverProfile caregiver, Shift shift, boolean enforceChannel) {
        QualificationRulePack pack = rulePackService.getOrCreate(shift.getRequiredQualification());

        if (!caregiver.getQualifications().contains(shift.getRequiredQualification())) {
            throw new BadRequestException(
                    "Caregiver does not have the required qualification: "
                            + shift.getRequiredQualification());
        }

        if (enforceChannel) {
            assertChannelAllows(pack, shift);
        }
        assertCredentials(caregiver, pack);
        assertGeo(caregiver, shift, pack);
    }

    private void assertChannelAllows(QualificationRulePack pack, Shift shift) {
        ShiftChannel preferred = pack.getPreferredChannel();
        if (preferred == ShiftChannel.BOTH) {
            return;
        }
        boolean facilityShift = shift.getFacilityProfileId() != null;
        boolean homeShift = shift.getClientProfileId() != null && !facilityShift;
        if (preferred == ShiftChannel.FACILITY && homeShift) {
            throw new BadRequestException(
                    pack.getQualification() + " rule pack prefers facility shifts; "
                            + "this shift is a home/client posting");
        }
        if (preferred == ShiftChannel.HOME && facilityShift) {
            throw new BadRequestException(
                    pack.getQualification() + " rule pack prefers home shifts; "
                            + "this shift is a facility posting");
        }
    }

    private void assertCredentials(CaregiverProfile caregiver, QualificationRulePack pack) {
        if (!pack.isEnforceCredentials()) {
            return;
        }
        Set<CredentialType> required = pack.getRequiredCredentials();
        if (required == null || required.isEmpty()) {
            return;
        }
        List<CaregiverCredential> vault = credentialRepository
                .findByCaregiverProfileIdOrderByCredentialTypeAsc(caregiver.getId());
        Map<CredentialType, CaregiverCredential> byType = vault.stream()
                .collect(Collectors.toMap(
                        CaregiverCredential::getCredentialType,
                        Function.identity(),
                        (a, b) -> a));

        LocalDate cutoff = LocalDate.now().plusDays(Math.max(0, pack.getCredentialExpiryBlockDays()));
        for (CredentialType type : required) {
            CaregiverCredential cred = byType.get(type);
            if (cred == null) {
                throw new BadRequestException("Missing required credential: " + type);
            }
            if (cred.getVerificationStatus() != CredentialVerificationStatus.APPROVED) {
                throw new BadRequestException(
                        "Credential " + type + " is not approved ("
                                + cred.getVerificationStatus() + ")");
            }
            if (cred.getExpiryDate() != null) {
                if (cred.getExpiryDate().isBefore(LocalDate.now())) {
                    throw new BadRequestException("Credential " + type + " has expired");
                }
                // Block when expiry is on or before (today + blockDays).
                if (!cred.getExpiryDate().isAfter(cutoff)) {
                    throw new BadRequestException(
                            "Credential " + type + " expires within "
                                    + pack.getCredentialExpiryBlockDays() + " days");
                }
            }
        }
    }

    private void assertGeo(CaregiverProfile caregiver, Shift shift, QualificationRulePack pack) {
        if (pack.getMatchingMode() == MatchingMode.DRIVE_TIME) {
            if (!driveTimeService.withinDriveTime(caregiver, shift, pack)) {
                throw new BadRequestException(
                        "This shift is outside the max drive time ("
                                + pack.getMaxDriveMinutes() + " min) for "
                                + pack.getQualification());
            }
            return;
        }
        int radius = driveTimeService.effectiveRadiusMiles(caregiver, shift);
        if (radius <= 0) {
            // Incomplete caregiver radius: do not block (same as GeoUtils).
            return;
        }
        if (!GeoUtils.withinRadiusMiles(
                caregiver.getHomeLat(), caregiver.getHomeLng(),
                shift.getLat(), shift.getLng(),
                radius)) {
            throw new BadRequestException(
                    "This shift is outside the caregiver's service area (jurisdiction)");
        }
    }
}
