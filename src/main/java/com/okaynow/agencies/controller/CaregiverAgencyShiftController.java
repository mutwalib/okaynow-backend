package com.okaynow.agencies.controller;

import com.okaynow.agencies.service.CaregiverAgencyShiftService;
import com.okaynow.booking.dto.ShiftClaimResponse;
import com.okaynow.shifts.dto.ShiftResponse;
import com.okaynow.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/caregivers/me/agency-shifts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CAREGIVER')")
public class CaregiverAgencyShiftController {

    private final CaregiverAgencyShiftService caregiverAgencyShiftService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<ShiftResponse>> listOpen(
            Authentication authentication,
            @RequestParam(required = false) UUID agencyId) {
        UUID userId = userService.getByEmail(authentication.getName()).getId();
        return ResponseEntity.ok(caregiverAgencyShiftService.listOpenRosterShifts(userId, agencyId));
    }

    @PostMapping("/{shiftId}/claim")
    public ResponseEntity<ShiftClaimResponse> claim(
            Authentication authentication, @PathVariable UUID shiftId) {
        UUID userId = userService.getByEmail(authentication.getName()).getId();
        return ResponseEntity.ok(caregiverAgencyShiftService.claimOpenRosterShift(userId, shiftId));
    }
}
