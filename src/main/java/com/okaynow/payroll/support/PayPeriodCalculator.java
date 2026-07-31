package com.okaynow.payroll.support;

import com.okaynow.payroll.domain.AgencySettings;
import com.okaynow.payroll.domain.PayPeriodType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public final class PayPeriodCalculator {

    private PayPeriodCalculator() {
    }

    public record Bounds(LocalDate start, LocalDate end) {
    }

    public static Bounds forDate(LocalDate date, AgencySettings settings) {
        DayOfWeek startDay = settings.getPeriodStartDay() != null
                ? settings.getPeriodStartDay()
                : DayOfWeek.MONDAY;
        LocalDate periodStart = date.with(TemporalAdjusters.previousOrSame(startDay));
        int days = settings.getPayPeriodType() == PayPeriodType.BIWEEKLY ? 13 : 6;
        LocalDate periodEnd = periodStart.plusDays(days);
        return new Bounds(periodStart, periodEnd);
    }

    /** Current agency pay period containing today. */
    public static Bounds current(AgencySettings settings) {
        return forDate(LocalDate.now(), settings);
    }

    /** Default stats window: 7 days ago through today (inclusive). */
    public static Bounds lastSevenDaysThroughToday() {
        LocalDate end = LocalDate.now();
        return new Bounds(end.minusDays(7), end);
    }
}
