package com.okaynow.agencies.service;

import com.okaynow.booking.domain.ShiftClaim;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.booking.repository.ShiftClaimRepository;
import com.okaynow.common.exception.ConflictException;
import com.okaynow.common.geo.GeoUtils;
import com.okaynow.evv.support.ShiftWindows;
import com.okaynow.marketplace.service.DriveTimeService;
import com.okaynow.payroll.domain.AgencySettings;
import com.okaynow.payroll.service.AgencySettingsService;
import com.okaynow.shifts.domain.Shift;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Agency-scoped staffing limits: incomplete shift caps and travel feasibility
 * between consecutive visits at different homes.
 */
@Service
@RequiredArgsConstructor
public class CaregiverStaffingConstraintService {

    private static final Set<ShiftClaimStatus> INCOMPLETE_STATUSES =
            EnumSet.of(ShiftClaimStatus.PENDING, ShiftClaimStatus.CONFIRMED);

    private final ShiftClaimRepository shiftClaimRepository;
    private final AgencySettingsService agencySettingsService;
    private final DriveTimeService driveTimeService;

    @Transactional(readOnly = true)
    public void assertAgencyStaffingRules(UUID agencyId, UUID caregiverProfileId, Shift candidate) {
        AgencySettings settings = agencySettingsService.getOrCreateForAgency(agencyId);
        assertMaxIncompleteShifts(agencyId, caregiverProfileId, candidate.getId(), settings);
        assertTravelBetweenShifts(caregiverProfileId, candidate, settings);
    }

    private void assertMaxIncompleteShifts(
            UUID agencyId, UUID caregiverProfileId, UUID excludeShiftId, AgencySettings settings) {
        int max = settings.getMaxIncompleteShiftsPerCaregiver();
        if (max <= 0) {
            return;
        }
        long count = shiftClaimRepository.countIncompleteForAgency(
                caregiverProfileId, agencyId, excludeShiftId, INCOMPLETE_STATUSES);
        if (count >= max) {
            throw new ConflictException(
                    "This caregiver already has " + count + " open shift(s) for your agency "
                            + "(limit " + max + "). Complete or release one before adding another.");
        }
    }

    private void assertTravelBetweenShifts(
            UUID caregiverProfileId, Shift candidate, AgencySettings settings) {
        int bufferMinutes = Math.max(0, settings.getMinBufferMinutesBetweenShifts());
        int maxDriveMinutes = settings.getMaxDriveMinutesBetweenShifts();
        if (maxDriveMinutes <= 0 && bufferMinutes <= 0) {
            return;
        }

        for (ShiftClaim existing : shiftClaimRepository.findActiveClaimsExcludingShift(
                caregiverProfileId, candidate.getId(), INCOMPLETE_STATUSES)) {
            Shift other = existing.getShift();
            if (ShiftWindows.overlaps(candidate, other)) {
                continue;
            }
            assertGapAndDrive(candidate, other, bufferMinutes, maxDriveMinutes);
        }
    }

    private void assertGapAndDrive(
            Shift candidate, Shift other, int bufferMinutes, int maxDriveMinutes) {
        Instant candidateStart = ShiftWindows.startInstant(candidate);
        Instant candidateEnd = ShiftWindows.endInstant(candidate);
        Instant otherStart = ShiftWindows.startInstant(other);
        Instant otherEnd = ShiftWindows.endInstant(other);

        if (otherEnd.compareTo(candidateStart) <= 0) {
            validateTransition(other, candidate, otherEnd, candidateStart, bufferMinutes, maxDriveMinutes);
        } else if (candidateEnd.compareTo(otherStart) <= 0) {
            validateTransition(candidate, other, candidateEnd, otherStart, bufferMinutes, maxDriveMinutes);
        }
    }

    private void validateTransition(
            Shift fromShift,
            Shift toShift,
            Instant fromEnd,
            Instant toStart,
            int bufferMinutes,
            int maxDriveMinutes) {
        if (!differentHomes(fromShift, toShift)) {
            return;
        }
        if (fromShift.getLat() == null
                || fromShift.getLng() == null
                || toShift.getLat() == null
                || toShift.getLng() == null) {
            return;
        }

        int driveMinutes = driveTimeService.estimateDriveMinutes(
                fromShift.getLat(), fromShift.getLng(), toShift.getLat(), toShift.getLng());
        if (maxDriveMinutes > 0 && driveMinutes > maxDriveMinutes) {
            throw new ConflictException(
                    "Drive time between " + fromShift.getCity() + " and " + toShift.getCity()
                            + " is about " + driveMinutes + " minutes — your agency limit is "
                            + maxDriveMinutes + " minutes between different homes.");
        }

        long gapMinutes = Duration.between(fromEnd, toStart).toMinutes();
        long requiredGap = driveMinutes + bufferMinutes;
        if (gapMinutes < requiredGap) {
            throw new ConflictException(
                    "Not enough time between shifts at different homes: need about "
                            + requiredGap + " minutes (drive " + driveMinutes + " min + "
                            + bufferMinutes + " min buffer) but only " + gapMinutes + " min available.");
        }
    }

    private static boolean differentHomes(Shift a, Shift b) {
        if (a.getLat() != null && a.getLng() != null && b.getLat() != null && b.getLng() != null) {
            return GeoUtils.distanceMiles(a.getLat(), a.getLng(), b.getLat(), b.getLng()) > 0.05;
        }
        if (a.getClientProfileId() != null && b.getClientProfileId() != null) {
            return !a.getClientProfileId().equals(b.getClientProfileId());
        }
        return !a.getAddressLine().equalsIgnoreCase(b.getAddressLine())
                || !a.getZip().equals(b.getZip());
    }
}
