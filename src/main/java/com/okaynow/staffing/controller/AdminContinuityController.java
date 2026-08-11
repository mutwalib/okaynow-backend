package com.okaynow.staffing.controller;

import com.okaynow.staffing.dto.ContinuityCaregiverSuggestion;
import com.okaynow.staffing.service.ContinuityScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/shifts/{shiftId}/suggested-caregivers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminContinuityController {

    private final ContinuityScoreService continuityScoreService;

    @GetMapping
    public ResponseEntity<List<ContinuityCaregiverSuggestion>> suggest(@PathVariable UUID shiftId) {
        return ResponseEntity.ok(continuityScoreService.suggestForShift(shiftId));
    }
}
