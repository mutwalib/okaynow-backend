package com.okaynow.evv.support;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ShiftWindowsDurationTest {

    @Test
    void overnightDurationFromElevenPmToNineAm() {
        assertThat(ShiftWindows.durationMinutes(
                LocalTime.of(23, 0),
                LocalTime.of(9, 0))).isEqualTo(10 * 60);
    }

    @Test
    void sameDayDuration() {
        assertThat(ShiftWindows.durationMinutes(
                LocalTime.of(9, 0),
                LocalTime.of(17, 0))).isEqualTo(8 * 60);
    }
}
