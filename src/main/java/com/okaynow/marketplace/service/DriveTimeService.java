package com.okaynow.marketplace.service;

import com.okaynow.common.geo.GeoUtils;
import com.okaynow.evv.support.ShiftWindows;
import com.okaynow.marketplace.domain.MatchingMode;
import com.okaynow.marketplace.domain.QualificationRulePack;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.users.domain.CaregiverProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Drive-time estimates for HHA/PCA home matching. Uses haversine × urban MA factor
 * until a Maps Distance Matrix adapter is configured.
 */
@Service
@RequiredArgsConstructor
public class DriveTimeService {

    /** ~24 mph average urban/suburban MA → ~2.5 min per mile. */
    private static final double MINUTES_PER_MILE = 2.5;

    public int estimateDriveMinutes(
            Double originLat, Double originLng,
            Double destLat, Double destLng) {
        if (originLat == null || originLng == null || destLat == null || destLng == null) {
            return 0;
        }
        double miles = GeoUtils.distanceMiles(originLat, originLng, destLat, destLng);
        return (int) Math.ceil(miles * MINUTES_PER_MILE);
    }

    public double estimateDriveMiles(
            Double originLat, Double originLng,
            Double destLat, Double destLng) {
        if (originLat == null || originLng == null || destLat == null || destLng == null) {
            return 0;
        }
        return GeoUtils.distanceMiles(originLat, originLng, destLat, destLng);
    }

    public boolean withinDriveTime(CaregiverProfile caregiver, Shift shift, QualificationRulePack pack) {
        if (pack.getMatchingMode() != MatchingMode.DRIVE_TIME) {
            return true;
        }
        Integer max = pack.getMaxDriveMinutes();
        if (max == null || max <= 0) {
            // Fall back to radius when drive cap is unset.
            return GeoUtils.withinRadiusMiles(
                    caregiver.getHomeLat(), caregiver.getHomeLng(),
                    shift.getLat(), shift.getLng(),
                    effectiveRadiusMiles(caregiver, shift));
        }
        int minutes = estimateDriveMinutes(
                caregiver.getHomeLat(), caregiver.getHomeLng(),
                shift.getLat(), shift.getLng());
        return minutes <= max;
    }

    public int effectiveRadiusMiles(CaregiverProfile caregiver, Shift shift) {
        int base = caregiver.getServiceRadiusMiles() != null ? caregiver.getServiceRadiusMiles() : 0;
        return base + Math.max(0, shift.getEscalationRadiusBonusMiles());
    }

    /** Travel pay for a one-way trip when the pack enables it. */
    public java.math.BigDecimal travelPayAmount(QualificationRulePack pack, int driveMinutes) {
        if (!pack.isTravelPayEnabled() || driveMinutes <= 0 || pack.getTravelPayPerMinute() == null) {
            return java.math.BigDecimal.ZERO;
        }
        return pack.getTravelPayPerMinute()
                .multiply(java.math.BigDecimal.valueOf(driveMinutes))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public long hoursUntilStart(Shift shift) {
        var start = shift.getDate().atTime(shift.getStartTime()).atZone(ShiftWindows.ZONE).toInstant();
        long seconds = java.time.Duration.between(java.time.Instant.now(), start).getSeconds();
        return seconds / 3600;
    }
}
