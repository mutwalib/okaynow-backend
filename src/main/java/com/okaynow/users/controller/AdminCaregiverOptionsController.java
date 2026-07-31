package com.okaynow.users.controller;

import com.okaynow.users.dto.CaregiverOptionResponse;
import com.okaynow.users.repository.CaregiverProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/caregiver-options")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCaregiverOptionsController {

    private final CaregiverProfileRepository caregiverProfileRepository;

    @GetMapping
    public ResponseEntity<List<CaregiverOptionResponse>> list() {
        return ResponseEntity.ok(caregiverProfileRepository.findAllWithUser().stream()
                .map(c -> new CaregiverOptionResponse(
                        c.getId(),
                        c.getFirstName(),
                        c.getLastName(),
                        c.getUser().getEmail(),
                        c.getQualifications(),
                        c.getServiceRadiusMiles(),
                        c.getHomeLat(),
                        c.getHomeLng()))
                .toList());
    }
}
