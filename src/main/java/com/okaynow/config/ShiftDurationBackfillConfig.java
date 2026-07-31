package com.okaynow.config;

import com.okaynow.shifts.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class ShiftDurationBackfillConfig implements ApplicationRunner {

    private final ShiftRepository shiftRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var shifts = shiftRepository.findAll().stream()
                .filter(shift -> shift.getDurationMinutes() == 0)
                .peek(shift -> shift.setDurationMinutes((int) ChronoUnit.MINUTES.between(
                        shift.getStartTime(), shift.getEndTime())))
                .toList();
        if (!shifts.isEmpty()) {
            shiftRepository.saveAll(shifts);
        }
    }
}
