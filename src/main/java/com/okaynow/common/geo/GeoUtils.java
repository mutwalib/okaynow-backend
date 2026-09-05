package com.okaynow.common.geo;

/**
 * Great-circle distance helpers for service-area and EVV geofence checks.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_MILES = 3958.7613;
    private static final double METERS_PER_MILE = 1609.344;

    /** Sandata-aligned visit radius (~250 ft). */
    public static final int EVV_GEOFENCE_FEET = 250;
    public static final double EVV_GEOFENCE_METERS = EVV_GEOFENCE_FEET * 0.3048;

    private GeoUtils() {
    }

    public static double distanceMiles(double lat1, double lng1, double lat2, double lng2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_MILES * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        return distanceMiles(lat1, lng1, lat2, lng2) * METERS_PER_MILE;
    }

    public static boolean withinRadiusMiles(
            Double originLat, Double originLng,
            Double targetLat, Double targetLng,
            Integer radiusMiles) {
        if (originLat == null || originLng == null
                || targetLat == null || targetLng == null
                || radiusMiles == null || radiusMiles <= 0) {
            // Incomplete geo data: do not block marketplace access.
            return true;
        }
        return distanceMiles(originLat, originLng, targetLat, targetLng) <= radiusMiles;
    }

    public static boolean withinRadiusMeters(
            double originLat, double originLng,
            double targetLat, double targetLng,
            double radiusMeters) {
        return distanceMeters(originLat, originLng, targetLat, targetLng) <= radiusMeters;
    }
}
