package com.okaynow.evv.support;

import com.okaynow.shifts.domain.Shift;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ShiftWindowsOverlapTest {

    @Test
    void abuttingSameDayWindowsDoNotOverlap() {
        Shift morning = shift(LocalTime.of(9, 0), LocalTime.of(15, 0));
        Shift evening = shift(LocalTime.of(15, 0), LocalTime.of(23, 0));
        assertThat(ShiftWindows.overlaps(morning, evening)).isFalse();
        assertThat(ShiftWindows.overlaps(evening, morning)).isFalse();
    }

    @Test
    void trueOverlapIsDetected() {
        Shift a = shift(LocalTime.of(9, 0), LocalTime.of(17, 0));
        Shift b = shift(LocalTime.of(15, 0), LocalTime.of(23, 0));
        assertThat(ShiftWindows.overlaps(a, b)).isTrue();
    }

    @Test
    void overnightAbutsNextMorning() {
        Shift overnight = shift(LocalTime.of(23, 0), LocalTime.of(9, 0));
        Shift morning = Shift.builder()
                .id(UUID.randomUUID())
                .date(LocalDate.of(2026, 7, 31))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(15, 0))
                .build();
        overnight.setDate(LocalDate.of(2026, 7, 30));
        assertThat(ShiftWindows.overlaps(overnight, morning)).isFalse();
    }

    private static Shift shift(LocalTime start, LocalTime end) {
        return Shift.builder()
                .id(UUID.randomUUID())
                .date(LocalDate.of(2026, 7, 30))
                .startTime(start)
                .endTime(end)
                .build();
    }
}
