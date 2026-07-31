package com.okaynow.evv.support;

import com.okaynow.shifts.domain.Shift;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Shift scheduled window helpers (Massachusetts local time). */
public final class ShiftWindows {

    public static final ZoneId ZONE = ZoneId.of("America/New_York");
    /** Minutes before start when caregiver may clock in. */
    public static final int EARLY_CLOCK_IN_MINUTES = 30;

    private ShiftWindows() {
    }

    public static LocalDateTime startLocal(Shift shift) {
        return LocalDateTime.of(shift.getDate(), shift.getStartTime());
    }

    public static LocalDateTime endLocal(Shift shift) {
        LocalDate date = shift.getDate();
        LocalTime start = shift.getStartTime();
        LocalTime end = shift.getEndTime();
        if (end != null && start != null && !end.isAfter(start)) {
            // Overnight: ends next calendar day
            return LocalDateTime.of(date.plusDays(1), end);
        }
        return LocalDateTime.of(date, end);
    }

    public static Instant startInstant(Shift shift) {
        return startLocal(shift).atZone(ZONE).toInstant();
    }

    public static Instant endInstant(Shift shift) {
        return endLocal(shift).atZone(ZONE).toInstant();
    }

    public static Instant earliestClockIn(Shift shift) {
        return startLocal(shift).minusMinutes(EARLY_CLOCK_IN_MINUTES).atZone(ZONE).toInstant();
    }

    /** Scheduled length in minutes; overnight when end is not after start. */
    public static int durationMinutes(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return 0;
        }
        long minutes = ChronoUnit.MINUTES.between(start, end);
        if (minutes <= 0) {
            minutes += 24 * 60L;
        }
        return (int) minutes;
    }

    public static int durationMinutes(Shift shift) {
        return durationMinutes(shift.getStartTime(), shift.getEndTime());
    }

    /** True when windows [aStart, aEnd) and [bStart, bEnd) overlap.
     * Abutting windows (e.g. 09:00–15:00 and 15:00–23:00) do not overlap. */
    public static boolean overlaps(Shift a, Shift b) {
        Instant aStart = startInstant(a);
        Instant aEnd = endInstant(a);
        Instant bStart = startInstant(b);
        Instant bEnd = endInstant(b);
        if (!aEnd.isAfter(aStart) || !bEnd.isAfter(bStart)) {
            return false;
        }
        // Explicit abutting: earlier ends exactly when later starts.
        if (aEnd.equals(bStart) || bEnd.equals(aStart)) {
            return false;
        }
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    /** True when two shifts abut: earlier ends when later starts. */
    public static boolean beginsImmediatelyAfter(Shift earlier, Shift later) {
        return endLocal(earlier).equals(startLocal(later));
    }
}
