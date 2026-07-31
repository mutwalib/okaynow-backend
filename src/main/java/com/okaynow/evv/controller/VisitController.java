package com.okaynow.evv.controller;

import com.okaynow.evv.dto.ClientAttendanceRequest;
import com.okaynow.evv.dto.ClockInRequest;
import com.okaynow.evv.dto.ClockOutRequest;
import com.okaynow.evv.dto.VisitResponse;
import com.okaynow.evv.service.VisitService;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/visits")
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;
    private final UserService userService;

    @GetMapping("/by-shift/{shiftId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAREGIVER', 'CLIENT', 'FACILITY')")
    public ResponseEntity<VisitResponse> getByShift(
            @PathVariable UUID shiftId, Authentication authentication) {
        VisitResponse visit = visitService.getByShiftId(
                shiftId, userService.getByEmail(authentication.getName()));
        if (visit == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(visit);
    }

    @PostMapping("/by-shift/{shiftId}/clock-in")
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<VisitResponse> clockIn(
            @PathVariable UUID shiftId,
            @RequestBody(required = false) ClockInRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                visitService.clockIn(shiftId, authentication.getName(),
                        request != null ? request : new ClockInRequest(null, null, null)));
    }

    @PostMapping("/by-shift/{shiftId}/clock-out")
    @PreAuthorize("hasRole('CAREGIVER')")
    public ResponseEntity<VisitResponse> clockOut(
            @PathVariable UUID shiftId,
            @RequestBody(required = false) ClockOutRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(visitService.clockOut(
                shiftId, authentication.getName(),
                request != null ? request : new ClockOutRequest(null, null)));
    }

    @PostMapping("/by-shift/{shiftId}/confirm-arrival")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<VisitResponse> confirmArrival(
            @PathVariable UUID shiftId, Authentication authentication) {
        return ResponseEntity.ok(visitService.confirmArrival(shiftId, authentication.getName()));
    }

    @PostMapping("/by-shift/{shiftId}/client-attendance")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<VisitResponse> clientAttendance(
            @PathVariable UUID shiftId,
            @Valid @RequestBody ClientAttendanceRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(visitService.recordClientAttendance(
                shiftId, authentication.getName(), request));
    }
}
