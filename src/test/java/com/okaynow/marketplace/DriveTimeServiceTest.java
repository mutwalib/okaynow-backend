package com.okaynow.marketplace;

import com.okaynow.common.geo.GeoUtils;
import com.okaynow.marketplace.domain.MatchingMode;
import com.okaynow.marketplace.domain.QualificationRulePack;
import com.okaynow.marketplace.service.DriveTimeService;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.Qualification;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriveTimeServiceTest {

    private final DriveTimeService driveTimeService = new DriveTimeService();

    @Test
    void estimatesMinutesFromHaversine() {
        // ~10 miles east of Boston roughly
        int minutes = driveTimeService.estimateDriveMinutes(
                42.3601, -71.0589, 42.3601, -70.8589);
        assertTrue(minutes > 20);
        assertTrue(minutes < 40);
    }

    @Test
    void travelPayScalesWithMinutes() {
        QualificationRulePack pack = QualificationRulePack.builder()
                .qualification(Qualification.HHA)
                .matchingMode(MatchingMode.DRIVE_TIME)
                .travelPayEnabled(true)
                .travelPayPerMinute(new BigDecimal("0.35"))
                .maxDriveMinutes(40)
                .build();
        assertEquals(new BigDecimal("7.00"), driveTimeService.travelPayAmount(pack, 20));
        assertEquals(BigDecimal.ZERO, driveTimeService.travelPayAmount(pack, 0));
    }

    @Test
    void driveTimeCapUsesMaxMinutes() {
        QualificationRulePack pack = QualificationRulePack.builder()
                .qualification(Qualification.PCA)
                .matchingMode(MatchingMode.DRIVE_TIME)
                .maxDriveMinutes(15)
                .build();
        CaregiverProfile cg = CaregiverProfile.builder()
                .homeLat(42.3601)
                .homeLng(-71.0589)
                .serviceRadiusMiles(50)
                .build();
        Shift near = Shift.builder()
                .lat(42.3650)
                .lng(-71.0500)
                .escalationRadiusBonusMiles(0)
                .build();
        Shift far = Shift.builder()
                .lat(42.3601)
                .lng(-70.7000)
                .escalationRadiusBonusMiles(0)
                .build();
        assertTrue(driveTimeService.withinDriveTime(cg, near, pack));
        assertFalse(driveTimeService.withinDriveTime(cg, far, pack));
        assertTrue(GeoUtils.distanceMiles(
                cg.getHomeLat(), cg.getHomeLng(), far.getLat(), far.getLng()) > 10);
    }
}
