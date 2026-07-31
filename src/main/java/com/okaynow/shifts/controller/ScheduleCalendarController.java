package com.okaynow.shifts.controller;

import com.okaynow.shifts.dto.ScheduleDayResponse;
import com.okaynow.shifts.service.ScheduleCalendarService;
import com.okaynow.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleCalendarController {

    private final ScheduleCalendarService scheduleCalendarService;
    private final UserService userService;

    @GetMapping("/calendar")
    @PreAuthorize("hasAnyRole('CLIENT', 'FACILITY', 'ADMIN')")
    public ResponseEntity<List<ScheduleDayResponse>> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID clientProfileId,
            @RequestParam(required = false) UUID facilityProfileId,
            Authentication authentication) {
        return ResponseEntity.ok(scheduleCalendarService.calendar(
                from,
                to,
                clientProfileId,
                facilityProfileId,
                userService.getByEmail(authentication.getName())));
    }
}
